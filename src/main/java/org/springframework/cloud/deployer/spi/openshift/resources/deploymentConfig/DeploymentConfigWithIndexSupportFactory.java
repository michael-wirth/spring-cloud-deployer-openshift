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

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.ImagePullPolicy;
import org.springframework.cloud.deployer.spi.openshift.DataflowSupport;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.resources.volumes.VolumeFactory;

/**
 * DeploymentConfig with index factory.
 *
 * @author Donovan Muller
 */
public class DeploymentConfigWithIndexSupportFactory extends DeploymentConfigFactory
		implements DataflowSupport {

	public DeploymentConfigWithIndexSupportFactory(OpenShiftClient client,
			OpenShiftDeployerProperties openShiftDeployerProperties, Container container,
			Map<String, String> labels, ResourceRequirements resourceRequirements,
			ImagePullPolicy imagePullPolicy) {
		super(client, container, labels, resourceRequirements, imagePullPolicy,
				new VolumeFactory(openShiftDeployerProperties));
	}

	@Override
	public DeploymentConfig addObject(AppDeploymentRequest request, String appId) {
		if (isIndexed(request)) {
			Integer count = getAppInstanceCount(request);
			for (int index = 0; index < count; index++) {
				String indexedId = appId + "-" + index;
				super.addObject(request, indexedId);
			}
		}
		else {
			super.addObject(request, appId);
		}

		return null;
	}

	@Override
	public void applyObject(AppDeploymentRequest request, String appId) {
		if (isIndexed(request)) {
			Integer count = getAppInstanceCount(request);
			for (int index = 0; index < count; index++) {
				String indexedId = appId + "-" + index;
				super.applyObject(request, indexedId);
			}
		}
		else {
			super.applyObject(request, appId);
		}
	}

	@Override
	protected DeploymentConfig build(AppDeploymentRequest request, String appId,
			Container container, Map<String, String> labels,
			ResourceRequirements resourceRequirements, ImagePullPolicy imagePullPolicy) {
		labels.replace("spring-deployment-id", appId);
		container.setName(appId);

		Optional<EnvVar> instanceIndexEnvVar = container.getEnv().stream()
				.filter((envVar) -> envVar.getName()
						.equals(AppDeployer.INSTANCE_INDEX_PROPERTY_KEY))
				.findFirst();
		String instanceIndex = NumberUtils
				.isCreatable(StringUtils.substringAfterLast(appId, "-"))
						? StringUtils.substringAfterLast(appId, "-")
						: Integer.valueOf(0).toString();
		if (instanceIndexEnvVar.isPresent()) {
			instanceIndexEnvVar.get().setValue(instanceIndex);
		}
		else {
			container.getEnv().add(new EnvVar(AppDeployer.INSTANCE_INDEX_PROPERTY_KEY,
					instanceIndex, null));
			container.getEnv()
					.add(new EnvVar("SPRING_APPLICATION_INDEX", instanceIndex, null));
		}

		return super.build(request, appId, container, labels, resourceRequirements,
				imagePullPolicy);
	}

	protected Integer getReplicas(AppDeploymentRequest request) {
		return isIndexed(request) ? 1 : getAppInstanceCount(request);
	}

}
