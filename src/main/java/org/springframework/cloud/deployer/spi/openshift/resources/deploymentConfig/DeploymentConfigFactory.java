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
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentTriggerPolicyBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.lang3.StringUtils;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.ImagePullPolicy;
import org.springframework.cloud.deployer.spi.openshift.DataflowSupport;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeploymentPropertyKeys;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftSupport;
import org.springframework.cloud.deployer.spi.openshift.resources.ObjectFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.volumes.VolumeFactory;

/**
 * DeploymentConfig factory.
 *
 * @author Donovan Muller
 */
public class DeploymentConfigFactory
		implements ObjectFactory<DeploymentConfig>, OpenShiftSupport, DataflowSupport {

	private OpenShiftClient client;

	private Container container;

	private Map<String, String> labels;

	private ResourceRequirements resourceRequirements;

	private ImagePullPolicy imagePullPolicy;

	private VolumeFactory volumeFactory;

	public DeploymentConfigFactory(OpenShiftClient client, Container container,
			Map<String, String> labels, ResourceRequirements resourceRequirements,
			ImagePullPolicy imagePullPolicy, VolumeFactory volumeFactory) {
		this.client = client;
		this.container = container;
		this.labels = labels;
		this.resourceRequirements = resourceRequirements;
		this.imagePullPolicy = imagePullPolicy;
		this.volumeFactory = volumeFactory;
	}

	@Override
	public DeploymentConfig addObject(AppDeploymentRequest request, String appId) {
		DeploymentConfig deploymentConfig = build(request, appId, this.container,
				this.labels, this.resourceRequirements, this.imagePullPolicy);

		if (getExisting(appId).isPresent()) {
			deploymentConfig = this.client.deploymentConfigs()
					.createOrReplace(deploymentConfig);
		}
		else {
			deploymentConfig = this.client.deploymentConfigs().create(deploymentConfig);
		}

		return deploymentConfig;
	}

	@Override
	public void applyObject(AppDeploymentRequest request, String appId) {
	}

	protected Optional<DeploymentConfig> getExisting(String name) {
		return Optional.ofNullable(
				this.client.deploymentConfigs().withName(name).fromServer().get());
	}

	protected DeploymentConfig build(AppDeploymentRequest request, String appId,
			Container container, Map<String, String> labels,
			ResourceRequirements resourceRequirements, ImagePullPolicy imagePullPolicy) {
		container.setResources(resourceRequirements);
		container.setImagePullPolicy(imagePullPolicy.name());

		//@formatter:off
		return new DeploymentConfigBuilder()
			.withNewMetadata()
				.withName(appId)
				.withLabels(labels)
			.endMetadata()
			.withNewSpec()
				.withTriggers(ImmutableList.of(
					new DeploymentTriggerPolicyBuilder()
						.withType("ConfigChange")
						.build()))
				.withNewStrategy()
					.withType("Rolling")
					.withResources(resourceRequirements)
				.endStrategy()
				.withReplicas(getReplicas(request))
				.withSelector(labels)
				.withNewTemplate()
					.withNewMetadata()
						.withLabels(labels)
					.endMetadata()
					.withNewSpec()
						.withContainers(container)
						.withRestartPolicy("Always")
						.withServiceAccount(request.getDeploymentProperties()
								.getOrDefault(OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_SERVICE_ACCOUNT,
										StringUtils.EMPTY))
						// only add volumes with corresponding volume mounts
						.withVolumes(this.volumeFactory.addObject(request, appId).stream()
							.filter((volume) -> container.getVolumeMounts().stream()
									.anyMatch((volumeMount) -> volumeMount.getName().equals(volume.getName())))
							.collect(Collectors.toList()))
					.endSpec()
				.endTemplate()
			.endSpec()
			.build();
		//@formatter:on
	}

	protected Integer getReplicas(AppDeploymentRequest request) {
		return getAppInstanceCount(request);
	}

}
