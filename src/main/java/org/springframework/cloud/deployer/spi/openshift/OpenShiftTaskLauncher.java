package org.springframework.cloud.deployer.spi.openshift;

import com.google.common.collect.Maps;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.ContainerFactory;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesTaskLauncher;
import org.springframework.cloud.deployer.spi.openshift.resources.ObjectFactory;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenShiftTaskLauncher extends KubernetesTaskLauncher
		implements TaskLauncher {

	private OpenShiftDeployerProperties openShiftDeployerProperties;

	private OpenShiftClient client;

	public OpenShiftTaskLauncher(OpenShiftDeployerProperties properties,
			OpenShiftClient client, ContainerFactory containerFactory) {
		super(properties, client, containerFactory);
		this.openShiftDeployerProperties = properties;
		this.client = client;
	}

	@Override
	public String launch(AppDeploymentRequest request) {
		logger.info(String.format("Launching task: '%s'", request.getDefinition()));

		String appId = createDeploymentId(request);

		Map<String, String> deploymentProperties = new HashMap();
		deploymentProperties.putAll(request.getDeploymentProperties());
		deploymentProperties.put(SPRING_APP_KEY, appId);
		AppDeploymentRequest taskDeploymentRequest = new AppDeploymentRequest(
				request.getDefinition(), request.getResource(), deploymentProperties,
				request.getCommandlineArguments());

		List<ObjectFactory> factories = populateOpenShiftObjects(taskDeploymentRequest,
				appId);
		factories.forEach(factory -> factory.addObject(taskDeploymentRequest, appId));
		factories.forEach(factory -> factory.applyObject(taskDeploymentRequest, appId));

		return appId;
	}

	protected String launchDockerResource(AppDeploymentRequest request) {
		return super.launch(request);
	}

	@Override
	public TaskStatus status(String id) {
		return super.status(id);
	}

	@Override
	public void cleanup(String id) {
		client.builds().list().getItems()
				.forEach(build -> client.builds().withName(id).cascading(true).delete());

		super.cleanup(id);
	}

	@Override
	protected String createDeploymentId(AppDeploymentRequest request) {
		String appId = request.getDeploymentProperties().get(SPRING_APP_KEY);
		if (appId == null) {
			appId = super.createDeploymentId(request);
		}
		return appId;
	}

	/**
	 * Populate the OpenShift objects that will be created/updated and applied.
	 * @param request
	 * @param taskId
	 * @return list of {@link ObjectFactory}'s
	 */
	protected List<ObjectFactory> populateOpenShiftObjects(AppDeploymentRequest request,
			String taskId) {
		List<ObjectFactory> factories = new ArrayList<>();

		factories.add(new ObjectFactory() {

			@Override
			public Object addObject(AppDeploymentRequest request, String appId) {
				// don't need to create anything
				return null;
			}

			@Override
			public void applyObject(AppDeploymentRequest request, String taskId) {
				AppDeploymentRequest taskDeploymentRequest = new AppDeploymentRequest(
						request.getDefinition(), request.getResource(),
						request.getDeploymentProperties(),
						request.getCommandlineArguments());

				launchDockerResource(taskDeploymentRequest);
			}
		});

		return factories;
	}

	protected OpenShiftClient getClient() {
		return client;
	}

	protected OpenShiftDeployerProperties getProperties() {
		return openShiftDeployerProperties;
	}

}
