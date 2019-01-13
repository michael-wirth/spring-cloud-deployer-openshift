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

package org.springframework.cloud.deployer.spi.openshift.resources.buildConfig;

import java.io.IOException;
import java.util.Map;

import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftAppDeployer;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeploymentPropertyKeys;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftSupport;

/**
 * Source to images BuildConfig strategy.
 *
 * @author Donovan Muller
 */
public class S2iBinaryInputBuildConfigStrategy extends BuildConfigStrategy
		implements OpenShiftSupport {

	private static Logger logger = LoggerFactory.getLogger(OpenShiftAppDeployer.class);

	private final OpenShiftDeployerProperties openShiftDeployerProperties;

	private final MavenResource mavenResource;

	private OpenShiftClient client;

	public S2iBinaryInputBuildConfigStrategy(
			OpenShiftDeployerProperties openShiftDeployerProperties,
			OpenShiftClient client, Map<String, String> labels,
			MavenResource mavenResource) {
		super(null, client, labels);
		this.client = client;
		this.openShiftDeployerProperties = openShiftDeployerProperties;
		this.mavenResource = mavenResource;
	}

	@Override
	protected BuildConfig buildBuildConfig(AppDeploymentRequest request, String appId,
			Map<String, String> labels) {
		//@formatter:off
		return new BuildConfigBuilder()
			.withNewMetadata()
				.withName(appId)
				.withLabels(labels)
			.endMetadata()
			.withNewSpec()
				.withNewSource()
					.withType("binary")
				.endSource()
				.withNewStrategy()
					.withNewSourceStrategy()
						.withNewFrom()
							.withKind("DockerImage")
							.withName(request.getDeploymentProperties().getOrDefault(
						OpenShiftDeploymentPropertyKeys.OPENSHIFT_S2I_BUILD_IMAGE,
						this.openShiftDeployerProperties.getDefaultS2iImage()))
						.endFrom()
					.endSourceStrategy()
				.endStrategy()
				.withNewOutput()
					.withNewTo()
						.withKind("ImageStreamTag")
						.withName(getImageTag(request, this.openShiftDeployerProperties, appId))
					.endTo()
				.endOutput()
			.endSpec()
			.build();
		//@formatter:on
	}

	@Override
	public void applyObject(AppDeploymentRequest request, String appId) {
		try {
			this.client.buildConfigs().withName(appId).instantiateBinary()
					.asFile(this.mavenResource.getFilename())
					.fromFile(this.mavenResource.getFile());
		}
		catch (IOException ex) {
			logger.error(String.format("Could not access Maven artifact: %s",
					this.mavenResource.getFilename()), ex);
		}
	}

}
