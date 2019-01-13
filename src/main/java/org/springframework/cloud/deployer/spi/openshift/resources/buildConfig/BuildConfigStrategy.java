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
import java.util.Optional;

import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.client.OpenShiftClient;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.resources.ObjectFactory;

/**
 * Abstract BuildConfig strategy.
 *
 * @author Donovan Muller
 */
public abstract class BuildConfigStrategy implements ObjectFactory<BuildConfig> {

	private OpenShiftClient client;

	private BuildConfigFactory buildConfigFactory;

	private Map<String, String> labels;

	protected BuildConfigStrategy(BuildConfigFactory buildConfigFactory,
			OpenShiftClient client, Map<String, String> labels) {
		this.buildConfigFactory = buildConfigFactory;
		this.client = client;
		this.labels = labels;
	}

	@Override
	public BuildConfig addObject(AppDeploymentRequest request, String appId) {
		BuildConfig buildConfig = buildBuildConfig(request, appId, this.labels);

		// TODO test this!
		// if (getExisting(appId).isPresent()) {
		/**
		 * Replacing a BuildConfig doesn't currently work because of "already modified"
		 * issues. Need to investigate if there is a clean way around it. For now, delete
		 * and recreate...
		 */
		buildConfig = this.client.buildConfigs().createOrReplace(buildConfig);
		// client.buildConfigs().withName(appId).delete();
		// client.builds().withLabelIn("spring-app-id", appId).delete();
		// buildConfig = client.buildConfigs().create(buildConfig);
		// }
		// else {
		// buildConfig = client.buildConfigs().create(buildConfig);
		// }

		return buildConfig;
	}

	@Override
	public void applyObject(AppDeploymentRequest request, String appId) {
		this.client.buildConfigs().withName(appId)
				.instantiate(this.buildConfigFactory.buildBuildRequest(request, appId));
	}

	protected abstract BuildConfig buildBuildConfig(AppDeploymentRequest request,
			String appId, Map<String, String> labels);

	protected Optional<BuildConfig> getExisting(String name) {
		//@formatter:off
		BuildConfig value = this.client.buildConfigs()
				.withName(name)
				.fromServer()
				.get();

		return Optional.ofNullable(value);
		//@formatter:on
	}

}
