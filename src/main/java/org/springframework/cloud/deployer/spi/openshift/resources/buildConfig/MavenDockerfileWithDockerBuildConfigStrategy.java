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

import java.io.File;
import java.io.IOException;
import java.util.Map;

import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.lang3.StringUtils;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeploymentPropertyKeys;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

/**
 * Maven with Dockerfile BuildConfig strategy.
 *
 * @author Donovan Muller
 */
public class MavenDockerfileWithDockerBuildConfigStrategy
		extends DockerfileWithDockerBuildConfigStrategy {

	public MavenDockerfileWithDockerBuildConfigStrategy(
			BuildConfigFactory buildConfigFactory,
			OpenShiftDeployerProperties openShiftDeployerProperties,
			OpenShiftClient client, Map<String, String> labels) {
		super(buildConfigFactory, openShiftDeployerProperties, client, labels);
	}

	@Override
	protected String getDockerfile(AppDeploymentRequest request,
			OpenShiftDeployerProperties properties) {
		String dockerFile = request.getDeploymentProperties()
				.get(OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_DOCKERFILE);
		try {
			if (StringUtils.isNotBlank(dockerFile)) {
				if (new File(dockerFile).exists()) {
					dockerFile = resourceToString(new FileSystemResource(dockerFile));
				}
			}
		}
		catch (IOException ex) {
			throw new RuntimeException(
					String.format("Could not read Dockerfile at %s", dockerFile), ex);
		}

		return dockerFile;
	}

	private String resourceToString(Resource resource) throws IOException {
		return new String(FileCopyUtils.copyToByteArray(resource.getInputStream()));
	}

}
