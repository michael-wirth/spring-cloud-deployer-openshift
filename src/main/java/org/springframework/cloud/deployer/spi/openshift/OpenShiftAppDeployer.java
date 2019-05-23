/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.openshift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.Iterables;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ServiceList;
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
import org.springframework.cloud.deployer.spi.kubernetes.ContainerConfiguration;
import org.springframework.cloud.deployer.spi.kubernetes.ContainerFactory;
import org.springframework.cloud.deployer.spi.kubernetes.ImagePullPolicy;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesAppDeployer;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.resources.ObjectFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.deploymentConfig.DeploymentConfigFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.deploymentConfig.DeploymentConfigWithIndexSupportFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.route.RouteFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.service.ServiceWithIndexSupportFactory;
import org.springframework.util.StringUtils;

/**
 * A deployer that targets Openshift.
 *
 * @author Donovan Muller
 */
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

		System.out.println(status(appId));
		if (!status(appId).getState().equals(DeploymentState.unknown)) {
			throw new IllegalStateException(
					String.format("App '%s' is already deployed", appId));
		}

		List<ObjectFactory> factories = populateOpenShiftObjectsForDeployment(
				compatibleRequest, appId);
		factories.forEach((factory) -> factory.addObject(compatibleRequest, appId));
		factories.forEach((factory) -> factory.applyObject(compatibleRequest, appId));

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
		this.client.services().withLabelIn(SPRING_APP_KEY, appId).delete();
		this.client.routes().withLabelIn(SPRING_APP_KEY, appId).delete();
		for (DeploymentConfig deploymentConfig : this.client.deploymentConfigs()
				.withLabelIn(SPRING_APP_KEY, appId).list().getItems()) {
			scaleDownPod(this.client, deploymentConfig);
			this.client.deploymentConfigs()
					.withName(deploymentConfig.getMetadata().getName()).cascading(true)
					.withGracePeriod(0).delete();
		}

		/**
		 * Explicitly delete the Deployment's Pods. This is actually only relevant when
		 * running in Travis :( The OpenShift instance started there has issues unmounting
		 * the Pod's volumes and results in the Pod never getting out of a "Terminating"
		 * status. It shouldn't be applicable in an actual "real" OpenShift cluster.
		 */
		// @formatter:off
		this.client.pods().withLabelIn(SPRING_APP_KEY, appId).list().getItems().stream()
			.peek((pod) -> logger.debug("Deleting Pod: {}", pod.getMetadata().getName()))
			.forEach((pod) -> this.client.pods()
				.withName(pod.getMetadata().getName())
				.cascading(true)
				.withGracePeriod(0)
				.delete());
		//@formatter:on

		try {
			// Give some time for resources to be deleted.
			// This is nasty and probably should be investigated for a better solution
			Thread.sleep(this.openShiftDeployerProperties.getUndeployDelay());
		}
		catch (InterruptedException ex) {
		}
	}

	/**
	 * An {@link OpenShiftAppInstanceStatus} includes the Build phases in addition to the
	 * implementation in
	 * {@link org.springframework.cloud.deployer.spi.kubernetes.AbstractKubernetesDeployer#buildAppStatus}.
	 */
	@Override
	protected AppStatus buildAppStatus(String appId, PodList list, ServiceList services) {
		AppStatus.Builder statusBuilder = AppStatus.of(appId);

		List<Build> builds = this.client.builds().withLabelIn(SPRING_APP_KEY, appId)
				.list().getItems();
		Build build = (builds.isEmpty()) ? null : Iterables.getLast(builds);

		if (list == null) {
			statusBuilder.with(new OpenShiftAppInstanceStatus(null,
					this.openShiftDeployerProperties, build));
		}
		else if (list.getItems().isEmpty()) {
			this.client.replicationControllers().withLabelIn(SPRING_APP_KEY, appId).list()
					.getItems().stream().findFirst()
					.ifPresent((replicationController) -> {
						if (replicationController.getMetadata().getAnnotations()
								.get("openshift.io/deployment.phase").equals("Failed")) {
							statusBuilder.generalState(DeploymentState.failed);
						}
					});
		}
		else {
			for (Pod pod : list.getItems()) {
				statusBuilder.with(new OpenShiftAppInstanceStatus(pod,
						this.openShiftDeployerProperties, build));
			}
		}

		return statusBuilder.build();
	}

	/**
	 * Populate the OpenShift objects that will be created/updated and applied when
	 * deploying an app.
	 * @param request appDeploymentRequest
	 * @param appId appId
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
			factories.add(new RouteFactory(getClient(), this.openShiftDeployerProperties,
					externalPort, labels));
		}

		return factories;
	}

	protected DeploymentConfigFactory getDeploymentConfigFactory(
			AppDeploymentRequest request, Map<String, String> labels,
			Container container) {
		return new DeploymentConfigWithIndexSupportFactory(getClient(),
				this.openShiftDeployerProperties, container, labels,
				getResourceRequirements(request), getImagePullPolicy(request));
	}

	/**
	 * Create an OpenShift Route if either of these two deployment properties are
	 * specified.
	 *
	 * <ul>
	 * <li>spring.cloud.deployer.kubernetes.createLoadBalancer</li>
	 * <li>spring.cloud.deployer.openshift.createRoute</li>
	 * </ul>
	 * @param request appDeploymentRequest
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
		return this.client;
	}

	protected KubernetesDeployerProperties getProperties() {
		return this.properties;
	}

	protected ContainerFactory getContainerFactory() {
		return this.containerFactory;
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

	/*
	 * Scale down the pod first before deleting. If we don't scale down the Pod first, the
	 * next deployment that requires a Build will result in the previous deployment being
	 * scaled while the new build is on progress. The new build will trigger a deployment
	 * of the new app but having the old app deployed for a period of time during the
	 * build is not desirable.
	 */
	private void scaleDownPod(OpenShiftClient client, DeploymentConfig deploymentConfig) {
		Future<Object> scaleDownTask = null;
		try {
			scaleDownTask = this.executorService.submit(() -> this.client
					.deploymentConfigs()
					.withName(deploymentConfig.getMetadata().getName()).scale(0, true));
			// TODO make this a deployer property
			scaleDownTask.get(30, TimeUnit.SECONDS);
		}
		catch (TimeoutException ex) {
			scaleDownTask.cancel(true);
		}
		catch (InterruptedException | ExecutionException ex) {
			throw new RuntimeException(ex);
		}
	}

}
