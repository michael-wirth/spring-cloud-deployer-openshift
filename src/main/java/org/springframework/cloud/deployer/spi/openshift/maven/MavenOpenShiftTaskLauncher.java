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

package org.springframework.cloud.deployer.spi.openshift.maven;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.lang3.StringUtils;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.ContainerFactory;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeploymentPropertyKeys;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftTaskLauncher;
import org.springframework.cloud.deployer.spi.openshift.ResourceHash;
import org.springframework.cloud.deployer.spi.openshift.resources.ObjectFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.buildConfig.BuildConfigStrategy;
import org.springframework.cloud.deployer.spi.openshift.resources.buildConfig.BuildStrategies;
import org.springframework.cloud.deployer.spi.openshift.resources.buildConfig.MavenBuildConfigFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.buildConfig.S2iBinaryInputBuildConfigStrategy;
import org.springframework.cloud.deployer.spi.openshift.resources.buildConfig.WatchingBuildConfigStrategy;
import org.springframework.cloud.deployer.spi.openshift.resources.imageStream.ImageStreamFactory;

public class MavenOpenShiftTaskLauncher extends OpenShiftTaskLauncher {

	private final OpenShiftDeployerProperties openShiftDeployerProperties;

	private final MavenResourceJarExtractor mavenResourceJarExtractor;

	private final MavenProperties mavenProperties;

	private final ResourceHash resourceHash;

	private final ContainerFactory containerFactory;

	public MavenOpenShiftTaskLauncher(
			OpenShiftDeployerProperties openShiftDeployerProperties,
			MavenProperties mavenProperties, OpenShiftClient client,
			MavenResourceJarExtractor mavenResourceJarExtractor,
			ResourceHash resourceHash, ContainerFactory containerFactory) {
		super(openShiftDeployerProperties, client, containerFactory);
		this.openShiftDeployerProperties = openShiftDeployerProperties;
		this.mavenResourceJarExtractor = mavenResourceJarExtractor;
		this.mavenProperties = mavenProperties;
		this.resourceHash = resourceHash;
		this.containerFactory = containerFactory;
	}

	@Override
	protected List<ObjectFactory> populateOpenShiftObjects(AppDeploymentRequest request,
			String taskId) {
		List<ObjectFactory> factories = new ArrayList<>();

		MavenResource mavenResource = (MavenResource) request.getResource();
		// because of random task names, there will never be an existing corresponding
		// build so we should always kick off a new build
		if (!buildExists(request, taskId, mavenResource)) {
			logger.info(String.format("Building application '%s' with resource: '%s'",
					taskId, mavenResource));

			factories.add(new ImageStreamFactory(getClient()));

			BuildStrategies buildStrategies = new BuildStrategies(this.mavenProperties,
					this.openShiftDeployerProperties, this.mavenResourceJarExtractor, this.resourceHash,
					getClient());
			BuildConfigStrategy buildStrategy = buildStrategies.chooseBuildStrategy(
					request, createIdMap(taskId, request), mavenResource);
			WatchingBuildConfigStrategy watchingBuildConfigStrategy = new WatchingBuildConfigStrategy(
					buildStrategy, getClient(), createIdMap(taskId, request),
					(build, watch) -> {
						if (buildStrategy instanceof S2iBinaryInputBuildConfigStrategy) {
							launchTask(build, watch, new AppDeploymentRequest(
									request.getDefinition(), request.getResource(),
									ImmutableMap.<String, String>builder()
											.putAll(request.getDeploymentProperties())
											.put("s2i-build", "true").build(),
									request.getCommandlineArguments()));
						}
						else {
							launchTask(build, watch, request);
						}
					});
			factories.add(watchingBuildConfigStrategy);
		}
		return factories;
	}

	// TODO there is allot of duplication with
	// org.springframework.cloud.deployer.spi.openshift.maven.MavenOpenShiftAppDeployer
	// we should probably extract the common functionality
	protected boolean buildExists(AppDeploymentRequest request, String appId,
			MavenResource mavenResource) {
		boolean buildExists;

		String forceBuild = request.getDeploymentProperties()
				.get(OpenShiftDeploymentPropertyKeys.OPENSHIFT_BUILD_FORCE);
		if (StringUtils.isAlpha(forceBuild)) {
			buildExists = !Boolean.parseBoolean(forceBuild.toLowerCase())
					|| !this.openShiftDeployerProperties.isForceBuild();
		}
		else {
			buildExists = getClient().builds().withLabelIn(SPRING_APP_KEY, appId).list()
					.getItems().stream()
					.filter((build) -> !build.getStatus().getPhase().equals("Failed"))
					.flatMap((build) -> build.getSpec().getStrategy().getDockerStrategy()
							.getEnv().stream()
							.filter((envVar) -> envVar.getName().equals(
									MavenBuildConfigFactory.SPRING_BUILD_ID_ENV_VAR)
									&& envVar.getValue().equals(
											this.resourceHash.hashResource(mavenResource))))
					.count() > 0;
		}

		return buildExists;
	}

	protected void launchTask(Build build, Watch watch, AppDeploymentRequest request) {
		if (build.getStatus().getPhase().equals("Complete")) {
			this.logger.info(
					String.format("Build complete: '%s'", build.getMetadata().getName()));

			DockerResource dockerResource = new DockerResource(
					build.getStatus().getOutputDockerImageReference());
			AppDeploymentRequest taskDeploymentRequest = new AppDeploymentRequest(
					request.getDefinition(), dockerResource,
					request.getDeploymentProperties(), request.getCommandlineArguments());

			launchDockerResource(taskDeploymentRequest);

			watch.close();
		}
	}

}
