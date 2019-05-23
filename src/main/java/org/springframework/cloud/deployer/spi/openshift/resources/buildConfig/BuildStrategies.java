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
import java.util.Optional;
import java.util.stream.Stream;

import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftApplicationPropertyKeys;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeploymentPropertyKeys;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftMavenDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.ResourceHash;
import org.springframework.cloud.deployer.spi.openshift.maven.GitReference;
import org.springframework.cloud.deployer.spi.openshift.maven.MavenResourceJarExtractor;
import org.springframework.core.io.Resource;

/**
 * Evaluates und provides the best matching build strategy.
 *
 * @author Donovan Muller
 */
public class BuildStrategies {

	private static final Logger logger = LoggerFactory.getLogger(BuildStrategies.class);

	private MavenProperties mavenProperties;

	private OpenShiftDeployerProperties deployerProperties;

	private MavenResourceJarExtractor mavenResourceJarExtractor;

	private ResourceHash resourceHash;

	private OpenShiftClient client;

	public BuildStrategies(MavenProperties mavenProperties,
			OpenShiftDeployerProperties deployerProperties,
			MavenResourceJarExtractor mavenResourceJarExtractor,
			ResourceHash resourceHash, OpenShiftClient client) {
		this.mavenProperties = mavenProperties;
		this.deployerProperties = deployerProperties;
		this.mavenResourceJarExtractor = mavenResourceJarExtractor;
		this.resourceHash = resourceHash;
		this.client = client;
	}

	public BuildConfigStrategy chooseBuildStrategy(AppDeploymentRequest request,
			Map<String, String> labels, MavenResource mavenResource) {
		Map<String, String> applicationProperties = request.getDefinition()
				.getProperties();

		return Stream
				.of(dockerfileFromProvidedGitRepoBuildConfig(applicationProperties,
						labels),
						dockerfileFromRemoteGitRepoBuildConfig(
								new OpenShiftMavenDeploymentRequest(request,
										this.mavenProperties),
								mavenResource, request, labels),
						dockerfileBuildConfig(request, labels))
				.filter(Optional::isPresent).findFirst()
				.orElse(Optional.of(new S2iBinaryInputBuildConfigStrategy(
						this.deployerProperties, this.client, labels, mavenResource)))
				.get();
	}

	private Optional<BuildConfigStrategy> dockerfileFromProvidedGitRepoBuildConfig(
			Map<String, String> applicationProperties, Map<String, String> labels) {
		Optional<BuildConfigStrategy> buildConfigFactory = Optional.empty();

		if (applicationProperties.containsKey(
				OpenShiftApplicationPropertyKeys.OPENSHIFT_BUILD_GIT_URI_PROPERTY)) {
			String gitUri = applicationProperties.get(
					OpenShiftApplicationPropertyKeys.OPENSHIFT_BUILD_GIT_URI_PROPERTY);
			String gitReferenceProperty = applicationProperties.getOrDefault(
					OpenShiftApplicationPropertyKeys.OPENSHIFT_BUILD_GIT_REF_PROPERTY,
					"master");
			GitReference gitReference = new GitReference(gitUri, gitReferenceProperty);
			MavenBuildConfigFactory mavenBuildConfigFactory = new MavenBuildConfigFactory(
					this.deployerProperties, this.resourceHash, this.mavenProperties);
			buildConfigFactory = Optional
					.of(new GitWithDockerBuildConfigStrategy(mavenBuildConfigFactory,
							gitReference, this.deployerProperties, this.client, labels));
		}

		return buildConfigFactory;
	}

	/**
	 * check the Maven artifact Jar for the presence of `src/main/docker/Dockerfile`, if
	 * it exists, it is an indication/assumption that the Dockerfile is present in a
	 * remote Git repository. OpenShift will use the actual remote repository as a Git
	 * Repository source.
	 */
	private Optional<BuildConfigStrategy> dockerfileFromRemoteGitRepoBuildConfig(
			OpenShiftMavenDeploymentRequest openShiftRequest, Resource mavenResource,
			AppDeploymentRequest request, Map<String, String> labels) {
		Optional<BuildConfigStrategy> buildConfigFactory = Optional.empty();

		GitReference gitReference = openShiftRequest.getGitReference();
		try {
			if (openShiftRequest.isMavenProjectExtractable()
					&& this.mavenResourceJarExtractor
							.extractFile(mavenResource, dockerfileLocation(request))
							.isPresent()) {
				/**
				 * extract Git URI and ref from <scm><connection>...</connection></scm>
				 * and <scm><tag>...</tag></scm> by parsing the Maven POM and use those
				 * values (as a {@link GitReference}) with Git Repository source strategy:
				 * https://docs.openshift.org/latest/dev_guide/builds.html#source-code
				 */
				MavenBuildConfigFactory mavenBuildConfigFactory = new MavenBuildConfigFactory(
						this.deployerProperties, this.resourceHash, this.mavenProperties);
				buildConfigFactory = Optional.of(new GitWithDockerBuildConfigStrategy(
						mavenBuildConfigFactory, gitReference, this.deployerProperties,
						this.client, labels));
			}
		}
		catch (IOException ex) {
			logger.error("Could not extract Git URI from Maven artifact", ex);
		}

		return buildConfigFactory;
	}

	private Optional<BuildConfigStrategy> dockerfileBuildConfig(
			AppDeploymentRequest request, Map<String, String> labels) {
		Optional<BuildConfigStrategy> buildConfigFactory = Optional.empty();

		if (request.getDeploymentProperties().containsKey(
				OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_DOCKERFILE)) {
			MavenBuildConfigFactory mavenBuildConfigFactory = new MavenBuildConfigFactory(
					this.deployerProperties, this.resourceHash, this.mavenProperties);
			buildConfigFactory = Optional
					.of(new MavenDockerfileWithDockerBuildConfigStrategy(
							mavenBuildConfigFactory, this.deployerProperties, this.client,
							labels));
		}

		return buildConfigFactory;
	}

	/**
	 * Get the source context directory, the path where the Dockerfile is expected.
	 * Defaults to the root directory.
	 * @param request application deployment spec
	 * @return the context directory/path where the Dockerfile is expected
	 */
	private String dockerfileLocation(AppDeploymentRequest request) {
		return request.getDefinition().getProperties().getOrDefault(
				OpenShiftApplicationPropertyKeys.OPENSHIFT_BUILD_GIT_DOCKERFILE_PATH,
				"Dockerfile");
	}

}
