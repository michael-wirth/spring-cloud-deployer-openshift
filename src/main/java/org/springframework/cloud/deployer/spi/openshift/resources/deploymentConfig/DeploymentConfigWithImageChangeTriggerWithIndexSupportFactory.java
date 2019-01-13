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

package org.springframework.cloud.deployer.spi.openshift.resources.deploymentConfig;

import java.util.Map;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentTriggerPolicy;
import io.fabric8.openshift.api.model.DeploymentTriggerPolicyBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.ImagePullPolicy;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;

/**
 * DeploymentConfig with image change trigger and index support factory.
 *
 * @author Donovan Muller
 */
public class DeploymentConfigWithImageChangeTriggerWithIndexSupportFactory
		extends DeploymentConfigWithIndexSupportFactory {

	private final OpenShiftClient client;

	private final OpenShiftDeployerProperties openShiftDeployerProperties;

	public DeploymentConfigWithImageChangeTriggerWithIndexSupportFactory(
			OpenShiftClient client,
			OpenShiftDeployerProperties openShiftDeployerProperties, Container container,
			Map<String, String> labels, ResourceRequirements resourceRequirements,
			ImagePullPolicy imagePullPolicy) {
		super(client, openShiftDeployerProperties, container, labels,
				resourceRequirements, imagePullPolicy);
		this.client = client;
		this.openShiftDeployerProperties = openShiftDeployerProperties;
	}

	@Override
	public void applyObject(AppDeploymentRequest request, String appId) {
		withIndexedDeployment(appId, request, (id, deploymentRequest) -> {
			// @formatter:off
				this.client.deploymentConfigs()
					.withName(id)
					.edit()
						.editSpec()
							.addToTriggers(buildTriggerPolicy(deploymentRequest, id, true))
						.endSpec()
				.done();
				//@formatter:on
		});
	}

	@Override
	protected DeploymentConfig build(AppDeploymentRequest request, String appId,
			Container container, Map<String, String> labels,
			ResourceRequirements resourceRequirements, ImagePullPolicy imagePullPolicy) {
		DeploymentConfig deploymentConfig = super.build(request, appId, container, labels,
				resourceRequirements, imagePullPolicy);
		//@formatter:off
		return new DeploymentConfigBuilder(deploymentConfig)
			.editSpec()
				.addToTriggers(buildTriggerPolicy(request, appId, false))
			.endSpec()
			.build();
		//@formatter:on
	}

	private DeploymentTriggerPolicy buildTriggerPolicy(AppDeploymentRequest request,
			String appId, Boolean automatic) {
		//@formatter:off
		return new DeploymentTriggerPolicyBuilder()
			.withType("ImageChange")
				.withNewImageChangeParams()
					.withContainerNames(appId)
					.withAutomatic(automatic)
					.withNewFrom()
						.withKind("ImageStreamTag")
						.withNamespace(getImageNamespace(request, this.openShiftDeployerProperties))
						.withName(getIndexedImageTag(request, this.openShiftDeployerProperties, appId))
					.endFrom()
				.endImageChangeParams()
			.build();
		//@formatter:on
	}

}
