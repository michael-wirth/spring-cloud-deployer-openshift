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

package org.springframework.cloud.deployer.spi.openshift.resources.pod;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.ContainerConfiguration;
import org.springframework.cloud.deployer.spi.kubernetes.DefaultContainerFactory;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftSupport;
import org.springframework.cloud.deployer.spi.openshift.resources.volumes.VolumeMountFactory;
import org.springframework.core.io.AbstractResource;

/**
 * Container factory.
 *
 * @author Donovan Muller
 */
public class OpenShiftContainerFactory extends DefaultContainerFactory
		implements OpenShiftSupport {

	private static final Logger logger = LoggerFactory
			.getLogger(OpenShiftContainerFactory.class);

	private OpenShiftDeployerProperties properties;

	private VolumeMountFactory volumeMountFactory;

	public OpenShiftContainerFactory(OpenShiftDeployerProperties properties,
			VolumeMountFactory volumeMountFactory) {
		super(properties);
		this.properties = properties;
		this.volumeMountFactory = volumeMountFactory;
	}

	@Override
	public Container create(ContainerConfiguration containerConfiguration) {
		Container container;
		AppDeploymentRequest request = containerConfiguration.getAppDeploymentRequest();
		if (request.getResource() instanceof DockerResource) {
			try {
				container = super.create(new ContainerConfiguration(
						containerConfiguration.getAppId(),
						new AppDeploymentRequest(request.getDefinition(),
								new OverridableDockerResource(
										request.getResource().getURI(),
										this.properties.getDockerRegistryOverride(),
										this.properties.getImageProjectName()),
								request.getDeploymentProperties(),
								request.getCommandlineArguments()))
										.withHostNetwork(
												containerConfiguration.isHostNetwork())
										.withExternalPort(containerConfiguration
												.getExternalPort()));
			}
			catch (IOException ex) {
				throw new IllegalArgumentException(
						"Unable to get URI for " + request.getResource(), ex);
			}
		}
		else {
			container = super.create(
					new ContainerConfiguration(containerConfiguration.getAppId(),
							new AppDeploymentRequest(request.getDefinition(),
									new AppIdResource(containerConfiguration.getAppId()),
									request.getDeploymentProperties(),
									request.getCommandlineArguments())).withHostNetwork(
											containerConfiguration.isHostNetwork())
											.withExternalPort(containerConfiguration
													.getExternalPort()));
		}

		if (request.getDeploymentProperties().containsKey("s2i-build")) {
			logger.info(
					"S2I build detected, setting the default cmd, required to pass command line args");

			container = new ContainerBuilder(container)
					.withCommand(this.properties.getContainerCommand()).build();
		}

		// use the VolumeMountFactory to resolve VolumeMounts because it has richer
		// support for things like using a Spring Cloud config server to resolve
		// VolumeMounts
		container.setVolumeMounts(this.volumeMountFactory.addObject(request,
				containerConfiguration.getAppId()));

		return container;
	}

	/**
	 * This allows the Kubernetes {@link DefaultContainerFactory} to be reused but still
	 * supporting BuildConfig used by the
	 * {@link org.springframework.cloud.deployer.spi.openshift.maven.MavenOpenShiftAppDeployer}.
	 * This resource allows the <code>appId</code> to be set as the Pod image, which is
	 * required for OpenShift to use the built image from the ImageStream after a
	 * successful Build.
	 */
	private class AppIdResource extends AbstractResource {

		private final String appId;

		AppIdResource(String appId) {
			this.appId = appId;
		}

		@Override
		public String getDescription() {
			return null;
		}

		@Override
		public InputStream getInputStream() {
			return null;
		}

		@Override
		public URI getURI() {
			try {
				return new URIBuilder("docker:" + this.appId).build();
			}
			catch (URISyntaxException ex) {
				throw new IllegalStateException(
						"Could not create masked URI for Maven build", ex);
			}
		}

	}

	private class OverridableDockerResource extends DockerResource {

		private final Logger log = LoggerFactory
				.getLogger(OverridableDockerResource.class);

		private final String dockerRegistry;

		private final String imageProjectName;

		OverridableDockerResource(URI uri, String dockerRegistry,
				String imageProjectName) {
			super(uri);
			this.dockerRegistry = dockerRegistry;
			this.imageProjectName = imageProjectName;
		}

		@Override
		public String getDescription() {
			return null;
		}

		@Override
		public InputStream getInputStream() {
			return null;
		}

		@Override
		public URI getURI() throws IOException {
			try {
				if (StringUtils.isNotBlank(this.dockerRegistry)) {
					this.log.debug(
							"Overriding docker registry with '{}' and project '{}'",
							this.dockerRegistry, this.imageProjectName);
					return new URIBuilder(super.getURI().toString()
							.replaceFirst("docker:(.*?)/",
									String.format("docker:%s/", this.dockerRegistry))
							.replaceFirst("/(.*?)/",
									String.format("/%s/", this.imageProjectName)))
											.build();
				}
				else {
					return super.getURI();
				}
			}
			catch (URISyntaxException ex) {
				throw new IllegalStateException(
						"Could not create masked URI for Maven build", ex);
			}
		}

	}

}
