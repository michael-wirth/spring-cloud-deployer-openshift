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

import io.fabric8.openshift.client.OpenShiftClient;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.ContainerFactory;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesTaskLauncher;
import org.springframework.cloud.deployer.spi.openshift.resources.ObjectFactory;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;

/**
 * A task launcher that targets Openshift.
 *
 * @author Donovan Muller
 */
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
		this.logger.info(String.format("Launching task: '%s'", request.getDefinition()));

		String appId = createDeploymentId(request);

		Map<String, String> deploymentProperties = new HashMap();
		deploymentProperties.putAll(request.getDeploymentProperties());
		deploymentProperties.put(SPRING_APP_KEY, appId);
		AppDeploymentRequest taskDeploymentRequest = new AppDeploymentRequest(
				request.getDefinition(), request.getResource(), deploymentProperties,
				request.getCommandlineArguments());

		List<ObjectFactory> factories = populateOpenShiftObjects(taskDeploymentRequest,
				appId);
		factories.forEach((factory) -> factory.addObject(taskDeploymentRequest, appId));
		factories.forEach((factory) -> factory.applyObject(taskDeploymentRequest, appId));

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
		this.client.builds().list().getItems().forEach(
				(build) -> this.client.builds().withName(id).cascading(true).delete());

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
	 * @param request application deployment sped
	 * @param taskId for deployment
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
		return this.client;
	}

	protected OpenShiftDeployerProperties getProperties() {
		return this.openShiftDeployerProperties;
	}

}
