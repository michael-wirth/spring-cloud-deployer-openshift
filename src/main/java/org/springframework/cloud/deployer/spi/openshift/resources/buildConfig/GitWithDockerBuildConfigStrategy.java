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
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftApplicationPropertyKeys;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftSupport;
import org.springframework.cloud.deployer.spi.openshift.maven.GitReference;
import org.springframework.util.StringUtils;

/**
 * Git repository with docker BuildConfig strategy.
 *
 * @author Donovan Muller
 */
public class GitWithDockerBuildConfigStrategy extends BuildConfigStrategy
		implements OpenShiftSupport {

	private BuildConfigFactory buildConfigFactory;

	private GitReference gitReference;

	private KubernetesDeployerProperties properties;

	public GitWithDockerBuildConfigStrategy(BuildConfigFactory buildConfigFactory,
			GitReference gitReference, KubernetesDeployerProperties properties,
			OpenShiftClient client, Map<String, String> labels) {
		super(buildConfigFactory, client, labels);
		this.buildConfigFactory = buildConfigFactory;
		this.gitReference = gitReference;
		this.properties = properties;
	}

	@Override
	protected BuildConfig buildBuildConfig(final AppDeploymentRequest request,
			final String appId, final Map<String, String> labels) {
		//@formatter:off
		BuildConfig buildConfig = new BuildConfigBuilder(this.buildConfigFactory.buildBuildConfig(request, appId, labels))
			.editSpec()
				.withNewSource()
					.withType("Git")
					.withNewGit()
						.withUri(this.gitReference.getParsedUri())
						.withRef(this.gitReference.getBranch())
					.endGit()
					.withContextDir(getContextDirectory(request))
				.endSource()
			.endSpec()
			.build();
		//@formatter:on

		String sourceSecret = getGitSourceSecret(request);
		if (StringUtils.hasText(sourceSecret)) {
			//@formatter:off
			buildConfig = new BuildConfigBuilder(buildConfig)
				.editSpec()
					.editSource()
						.withNewSourceSecret(getGitSourceSecret(request))
					.endSource()
				.endSpec()
			.build();
			//@formatter:on
		}

		//@formatter:off
		return new BuildConfigBuilder(buildConfig)
			.editSpec()
				.withNewStrategy()
					.withType("Docker")
					.withNewDockerStrategy()
					.endDockerStrategy()
				.endStrategy()
				.withNewOutput()
					.withNewTo()
						.withKind("ImageStreamTag")
						.withName(String.format("%s:%s", appId, "latest"))
					.endTo()
				.endOutput()
			.endSpec()
			.build();
		//@formatter:on
	}

	/**
	 * Get the source context directory, the path where the Dockerfile is expected.
	 * Defaults to src/main/docker
	 * @param request application deployment spec
	 * @return the context directory/path where the Dockerfile is expected
	 */
	protected String getContextDirectory(AppDeploymentRequest request) {
		return request.getDefinition().getProperties().getOrDefault(
				OpenShiftApplicationPropertyKeys.OPENSHIFT_BUILD_GIT_DOCKERFILE_PATH,
				"src/main/docker");
	}

	/**
	 * Attempt to get the Secret from the app deployment properties or from the deployer
	 * environment variables. See
	 * https://docs.openshift.org/latest/dev_guide/builds.html#using-secrets
	 * @param request application deployment spec
	 * @return a Secret if there is one available
	 */
	protected String getGitSourceSecret(AppDeploymentRequest request) {
		return request.getDefinition().getProperties().getOrDefault(
				OpenShiftApplicationPropertyKeys.OPENSHIFT_BUILD_GIT_SOURCE_SECRET,
				getEnvironmentVariable(this.properties.getEnvironmentVariables(),
						OpenShiftApplicationPropertyKeys.OPENSHIFT_BUILD_GIT_SOURCE_SECRET));
	}

}
