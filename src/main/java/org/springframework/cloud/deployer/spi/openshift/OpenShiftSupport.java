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

package org.springframework.cloud.deployer.spi.openshift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EnvVar;
import org.apache.commons.lang3.StringUtils;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.util.Assert;

/**
 * Support class for dataflow Openshift.
 *
 * @author Donovan Muller
 */
public interface OpenShiftSupport extends DataflowSupport {

	default String getImage(AppDeploymentRequest request, String appId) {
		return isIndexed(request) ? StringUtils.substringBeforeLast(appId, "-") : appId;
	}

	default String getImageTag(AppDeploymentRequest request,
			OpenShiftDeployerProperties properties, String appId) {
		return String.format("%s:%s", appId,
				request.getDeploymentProperties().getOrDefault(
						OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_IMAGE_TAG,
						properties.getDefaultImageTag()));
	}

	default String getIndexedImageTag(AppDeploymentRequest request,
			OpenShiftDeployerProperties properties, String appId) {
		return String.format("%s:%s", getImage(request, appId),
				request.getDeploymentProperties().getOrDefault(
						OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_IMAGE_TAG,
						properties.getDefaultImageTag()));
	}

	default String getImageNamespace(AppDeploymentRequest request,
			OpenShiftDeployerProperties properties) {
		return String.format("%s", request.getDeploymentProperties().getOrDefault(
				OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_IMAGE_NAMESPACE,
				properties.getDefaultImageNamespace()));
	}

	default String getEnvironmentVariable(String[] properties, String name) {
		return getEnvironmentVariable(properties, name, StringUtils.EMPTY);
	}

	default String getEnvironmentVariable(String[] properties, String name,
			String defaultValue) {
		return toEnvVars(properties).stream()
				.filter((envVar) -> envVar.getName().equals(name)).map(EnvVar::getValue)
				.findFirst().orElse(defaultValue);
	}

	default List<EnvVar> toEnvVars(String[] properties,
			Map<String, String>... overrideProperties) {
		Set<EnvVar> envVars = new HashSet<>();
		if (overrideProperties != null) {
			for (Map<String, String> overrideProperty : overrideProperties) {
				envVars.addAll(
						overrideProperty.entrySet().stream()
								.map((property) -> new EnvVar(property.getKey(),
										property.getValue(), null))
								.collect(Collectors.toList()));
			}
		}
		// bit backwards but overridden deployment EnvVar's will not be replaced by
		// deployer
		// property
		for (String envVar : properties) {
			String[] strings = envVar.split("=", 2);
			Assert.isTrue(strings.length == 2,
					"Invalid environment variable declared: " + envVar);
			envVars.add(new EnvVar(strings[0], strings[1], null));
		}

		return new ArrayList<>(envVars);
	}

	default Map<String, String> toLabels(Map<String, String> properties) {
		Map<String, String> labels = new HashMap<>();

		String labelsProperty = properties.getOrDefault(
				OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_LABELS,
				StringUtils.EMPTY);

		if (StringUtils.isNotBlank(labelsProperty)) {
			String[] labelPairs = labelsProperty.split(",");
			for (String labelPair : labelPairs) {
				String[] label = labelPair.split("=");
				Assert.isTrue(label.length == 2,
						String.format("Invalid label value: '{}'", labelPair));

				labels.put(label[0].trim(), label[1].trim());
			}
		}

		return labels;
	}

}
