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

import com.google.common.collect.ImmutableList;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildRequest;
import io.fabric8.openshift.api.model.BuildTriggerPolicyBuilder;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftSupport;

/**
 * Abstract BuildConfig factory.
 *
 * @author Donovan Muller
 */
public abstract class BuildConfigFactory implements OpenShiftSupport {

	public static String SPRING_BUILD_ID_ENV_VAR = "spring_build_id";

	public static String SPRING_BUILD_APP_NAME_ENV_VAR = "app_name";

	protected BuildConfig buildBuildConfig(AppDeploymentRequest request, String appId,
			Map<String, String> labels) {
		//@formatter:off
		return new BuildConfigBuilder()
			.withNewMetadata()
				.withName(appId)
				.withLabels(labels)
			.endMetadata()
			.withNewSpec()
				.withTriggers(ImmutableList.of(
						new BuildTriggerPolicyBuilder()
							.withNewImageChange()
							.endImageChange()
							.build()
				))
			.endSpec()
			.build();
		//@formatter:on
	}

	protected abstract BuildRequest buildBuildRequest(AppDeploymentRequest request,
			String appId);

}
