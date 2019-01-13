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

import java.util.Map;

import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;

/**
 * Dockerfile BuildConfig strategy.
 *
 * @author Donovan Muller
 */
public abstract class DockerfileWithDockerBuildConfigStrategy
		extends BuildConfigStrategy {

	private BuildConfigFactory buildConfigFactory;

	private OpenShiftDeployerProperties openShiftDeployerProperties;

	public DockerfileWithDockerBuildConfigStrategy(BuildConfigFactory buildConfigFactory,
			OpenShiftDeployerProperties openShiftDeployerProperties,
			OpenShiftClient client, Map<String, String> labels) {
		super(buildConfigFactory, client, labels);
		this.buildConfigFactory = buildConfigFactory;
		this.openShiftDeployerProperties = openShiftDeployerProperties;
	}

	@Override
	protected BuildConfig buildBuildConfig(AppDeploymentRequest request, String appId,
			Map<String, String> labels) {
		//@formatter:off
		return new BuildConfigBuilder(this.buildConfigFactory.buildBuildConfig(request, appId, labels))
				.editSpec()
				.withNewSource()
					.withType("Dockerfile")
					.withDockerfile(getDockerfile(request, this.openShiftDeployerProperties))
				.endSource()
				.withNewStrategy()
					.withType("Docker")
					.withNewDockerStrategy()
					.endDockerStrategy()
				.endStrategy()
				.withNewOutput()
					.withNewTo()
						.withKind("ImageStreamTag")
						.withName(this.buildConfigFactory.getImageTag(request, this.openShiftDeployerProperties, appId))
					.endTo()
				.endOutput()
			.endSpec()
			.build();
		//@formatter:on
	}

	/**
	 * Determine the Dockerfile (see https://docs.docker.com/engine/reference/builder/)
	 * source. The following sources are considered, in this order:
	 * <p>
	 * <ul>
	 * <li>spring.cloud.deployer.openshift.build.dockerfile deployment property. This can
	 * be an inline Dockerfile definition or a path to a Dockerfile on the file
	 * system</li>
	 * <li>The default Dockerfile bundled with the OpenShift deployer. See
	 * src/main/resources/Dockerfile</li>
	 * </ul>
	 * @param request application deployment spec
	 * @return an inline Dockerfile definition
	 */
	protected abstract String getDockerfile(AppDeploymentRequest request,
			OpenShiftDeployerProperties properties);

}
