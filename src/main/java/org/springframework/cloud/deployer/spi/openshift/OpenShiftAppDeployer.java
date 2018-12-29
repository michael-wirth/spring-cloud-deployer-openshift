package org.springframework.cloud.deployer.spi.openshift;

import com.google.common.collect.Iterables;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.*;
import org.springframework.cloud.deployer.spi.openshift.resources.ObjectFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.deploymentConfig.DeploymentConfigFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.deploymentConfig.DeploymentConfigWithIndexSuppportFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.route.RouteFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.service.ServiceWithIndexSupportFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class OpenShiftAppDeployer extends KubernetesAppDeployer
		implements AppDeployer, OpenShiftSupport {

	private static Logger logger = LoggerFactory.getLogger(OpenShiftAppDeployer.class);

	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private OpenShiftDeployerProperties openShiftDeployerProperties;

	private ContainerFactory containerFactory;

	private OpenShiftClient client;

	public OpenShiftAppDeployer(OpenShiftDeployerProperties properties,
			KubernetesClient client, ContainerFactory containerFactory) {
		super(properties, client);

		this.openShiftDeployerProperties = properties;
		this.client = (OpenShiftClient) client;
		this.containerFactory = containerFactory;
	}

	@Override
	public String deploy(AppDeploymentRequest request) {
		logger.info("Deploying application: {}", request.getDefinition());

		AppDeploymentRequest compatibleRequest = enableKubernetesDeployerCompatibility(
				request);
		validate(compatibleRequest);

		String appId = createDeploymentId(compatibleRequest);

		if (!status(appId).getState().equals(DeploymentState.unknown)) {
			throw new IllegalStateException(
					String.format("App '%s' is already deployed", appId));
		}

		List<ObjectFactory> factories = populateOpenShiftObjectsForDeployment(
				compatibleRequest, appId);
		factories.forEach(factory -> factory.addObject(compatibleRequest, appId));
		factories.forEach(factory -> factory.applyObject(compatibleRequest, appId));

		return appId;
	}

	@Override
	public void undeploy(String appId) {
		logger.info("Undeploying application: {}", appId);

		AppStatus status = status(appId);
		if (status.getState().equals(DeploymentState.unknown)) {
			throw new IllegalStateException(
					String.format("App '%s' is not deployed", appId));
		}

		// don't delete BuildConfig/Builds
		client.services().withLabelIn(SPRING_APP_KEY, appId).delete();
		client.routes().withLabelIn(SPRING_APP_KEY, appId).delete();
		for (DeploymentConfig deploymentConfig : client.deploymentConfigs()
				.withLabelIn(SPRING_APP_KEY, appId).list().getItems()) {
			scaleDownPod(client, deploymentConfig);
			client.deploymentConfigs().withName(deploymentConfig.getMetadata().getName())
					.cascading(true).withGracePeriod(0).delete();
		}

		/**
		 * Explicitly delete the Deployment's Pods. This is actually only relevant when
		 * running in Travis :( The OpenShift instance started there has issues unmounting
		 * the Pod's volumes and results in the Pod never getting out of a "Terminating"
		 * status. It shouldn't be applicable in an actual "real" OpenShift cluster.
		 */
		// @formatter:off
		this.client.pods().withLabelIn(SPRING_APP_KEY, appId).list().getItems().stream()
			.peek(pod -> logger.debug("Deleting Pod: {}", pod.getMetadata().getName()))
			.forEach(pod -> this.client.pods()
				.withName(pod.getMetadata().getName())
				.cascading(true)
				.withGracePeriod(0)
				.delete());
		//@formatter:on

		try {
			// Give some time for resources to be deleted.
			// This is nasty and probably should be investigated for a better solution
			Thread.sleep(openShiftDeployerProperties.getUndeployDelay());
		}
		catch (InterruptedException e) {
		}
	}

	/**
	 * An {@link OpenShiftAppInstanceStatus} includes the Build phases in addition to the
	 * implementation in
	 * {@link org.springframework.cloud.deployer.spi.kubernetes.AbstractKubernetesDeployer#buildAppStatus}
	 */
	@Override
	protected AppStatus buildAppStatus(String appId, PodList list, ServiceList services) {
		AppStatus.Builder statusBuilder = AppStatus.of(appId);

		List<Build> builds = client.builds().withLabelIn(SPRING_APP_KEY, appId).list()
				.getItems();
		Build build = (builds.isEmpty()) ? null : Iterables.getLast(builds);

		if (list == null) {
			statusBuilder.with(new OpenShiftAppInstanceStatus(null,
					openShiftDeployerProperties, build));
		}
		else if (list.getItems().isEmpty()) {
			this.client.replicationControllers().withLabelIn(SPRING_APP_KEY, appId).list()
					.getItems().stream().findFirst().ifPresent(replicationController -> {
						if (replicationController.getMetadata().getAnnotations()
								.get("openshift.io/deployment.phase").equals("Failed")) {
							statusBuilder.generalState(DeploymentState.failed);
						}
					});
		}
		else {
			for (Pod pod : list.getItems()) {
				statusBuilder.with(new OpenShiftAppInstanceStatus(pod,
						openShiftDeployerProperties, build));
			}
		}

		return statusBuilder.build();
	}

	/**
	 * Populate the OpenShift objects that will be created/updated and applied when
	 * deploying an app.
	 * @param request
	 * @param appId
	 * @return list of {@link ObjectFactory}'s
	 */
	protected List<ObjectFactory> populateOpenShiftObjectsForDeployment(
			AppDeploymentRequest request, String appId) {
		List<ObjectFactory> factories = new ArrayList<>();

		Map<String, String> labels = createIdMap(appId, request);
		labels.putAll(toLabels(request.getDeploymentProperties()));
		int externalPort = configureExternalPort(request);

		Container container = getContainerFactory()
				.create(new ContainerConfiguration(createDeploymentId(request), request)
						.withHostNetwork(false).withExternalPort(externalPort));
		logger.debug("Deploy container: {}", container);

		factories.add(getDeploymentConfigFactory(request, labels, container));
		factories.add(
				new ServiceWithIndexSupportFactory(getClient(), externalPort, labels));

		if (createRoute(request)) {
			factories.add(new RouteFactory(getClient(), openShiftDeployerProperties,
					externalPort, labels));
		}

		return factories;
	}

	protected DeploymentConfigFactory getDeploymentConfigFactory(
			AppDeploymentRequest request, Map<String, String> labels,
			Container container) {
		return new DeploymentConfigWithIndexSuppportFactory(getClient(),
				openShiftDeployerProperties, container, labels,
				getResourceRequirements(request), getImagePullPolicy(request));
	}

	/**
	 * Create an OpenShift Route if either of these two deployment properties are
	 * specified:
	 *
	 * <ul>
	 * <li>spring.cloud.deployer.kubernetes.createLoadBalancer</li>
	 * <li>spring.cloud.deployer.openshift.createRoute</li>
	 * </ul>
	 * @param request
	 * @return true if the Route object should be created
	 */
	protected boolean createRoute(AppDeploymentRequest request) {
		boolean createRoute = false;
		String createRouteProperty = request.getDeploymentProperties().getOrDefault(
				OpenShiftDeploymentPropertyKeys.KUBERNETES_CREATE_LOAD_BALANCER,
				request.getDeploymentProperties()
						.get(OpenShiftDeploymentPropertyKeys.OPENSHIFT_CREATE_ROUTE));
		if (StringUtils.isEmpty(createRouteProperty)) {
			createRoute = properties.isCreateLoadBalancer();
		}
		else {
			if (Boolean.parseBoolean(createRouteProperty.toLowerCase())) {
				createRoute = true;
			}
		}

		String createNodePort = request.getDeploymentProperties()
				.get(OpenShiftDeploymentPropertyKeys.OPENSHIFT_CREATE_NODE_PORT);
		if (createRoute && createNodePort != null) {
			throw new IllegalArgumentException(
					"Cannot create NodePort and LoadBalancer at the same time.");
		}

		return createRoute;
	}

	protected ResourceRequirements getResourceRequirements(AppDeploymentRequest request) {
		return new ResourceRequirements(deduceResourceLimits(request),
				deduceResourceRequests(request));
	}

	protected ImagePullPolicy getImagePullPolicy(AppDeploymentRequest request) {
		return deduceImagePullPolicy(request);
	}

	protected OpenShiftClient getClient() {
		return client;
	}

	protected KubernetesDeployerProperties getProperties() {
		return properties;
	}

	protected ContainerFactory getContainerFactory() {
		return containerFactory;
	}

	protected AppDeploymentRequest enableKubernetesDeployerCompatibility(
			AppDeploymentRequest request) {
		Map<String, String> kubernetesCompliantDeploymentProperties = new HashMap<>();
		request.getDeploymentProperties().forEach((key, value) -> {
			if (key.contains("spring.cloud.deployer.openshift")) {
				kubernetesCompliantDeploymentProperties
						.put(key.replace("openshift", "kubernetes"), value);
			}
		});
		kubernetesCompliantDeploymentProperties.putAll(request.getDeploymentProperties());
		return new AppDeploymentRequest(request.getDefinition(), request.getResource(),
				kubernetesCompliantDeploymentProperties,
				request.getCommandlineArguments());
	}

	private void validate(AppDeploymentRequest appDeploymentRequest) {
		if (appDeploymentRequest.getDefinition().getName().length() > 24) {
			throw new IllegalArgumentException(
					"Application name cannot be more than 24 characters");
		}
	}

	/**
	 * Scale down the pod first before deleting. If we don't scale down the Pod first, the
	 * next deployment that requires a Build will result in the previous deployment being
	 * scaled while the new build is on progress. The new build will trigger a deployment
	 * of the new app but having the old app deployed for a period of time during the
	 * build is not desirable.
	 */
	private void scaleDownPod(OpenShiftClient client, DeploymentConfig deploymentConfig) {
		Future<Object> scaleDownTask = null;
		try {
			scaleDownTask = executorService.submit(() -> client.deploymentConfigs()
					.withName(deploymentConfig.getMetadata().getName()).scale(0, true));
			// TODO make this a deployer property
			scaleDownTask.get(30, TimeUnit.SECONDS);
		}
		catch (TimeoutException e) {
			scaleDownTask.cancel(true);
		}
		catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

}
