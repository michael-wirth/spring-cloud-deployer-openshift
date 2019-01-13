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

package org.springframework.cloud.deployer.spi.openshift.resources.volumes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.api.model.VolumeMount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Volume mount factory.
 *
 * @author Donovan Muller
 */
public class VolumeMountConfigServerFactory extends VolumeMountFactory {

	private static final Logger logger = LoggerFactory
			.getLogger(VolumeMountConfigServerFactory.class);

	private ConfigServicePropertySourceLocator configServicePropertySourceLocator;

	public VolumeMountConfigServerFactory(
			ConfigServicePropertySourceLocator configServicePropertySourceLocator,
			OpenShiftDeployerProperties openShiftDeployerProperties) {
		super(openShiftDeployerProperties);
		this.configServicePropertySourceLocator = configServicePropertySourceLocator;
	}

	@Override
	public List<VolumeMount> addObject(AppDeploymentRequest request, String appId) {
		Set<VolumeMount> volumeMounts = new LinkedHashSet<>();
		volumeMounts.addAll(super.addObject(request, appId));
		volumeMounts.addAll(fetchVolumeMountsFromConfigServer(appId));
		return new ArrayList<>(volumeMounts);
	}

	private Set<VolumeMount> fetchVolumeMountsFromConfigServer(String appId) {
		Set<VolumeMount> volumeMounts = new LinkedHashSet<>();

		ConfigurableEnvironment appEnvironment = new StandardEnvironment();
		appEnvironment.getPropertySources()
				.addFirst(new MapPropertySource("deployer-openshift-override",
						Collections.singletonMap("spring.application.name", appId)));

		PropertySource<?> propertySource = this.configServicePropertySourceLocator
				.locate(appEnvironment);
		if (propertySource != null) {
			try {
				Binder binder = new Binder(
						ConfigurationPropertySources.from(propertySource));
				VolumeMountProperties configVolumeProperties = binder
						.bind("", VolumeMountProperties.class).get();
				volumeMounts.addAll(configVolumeProperties.getVolumeMounts());
			}
			catch (Exception ex) {
				logger.warn(
						"Could not get volume mounts configuration for app '{}' from config server: '{}'",
						appId, ex.getMessage());
			}
		}
		return volumeMounts;
	}

}
