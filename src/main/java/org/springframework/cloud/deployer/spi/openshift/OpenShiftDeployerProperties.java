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

import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.DeploymentConfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties;

/**
 * Openshift Deployer properties.
 *
 * @author Donovan Muller
 */
@ConfigurationProperties(prefix = "spring.cloud.deployer.openshift")
public class OpenShiftDeployerProperties extends KubernetesDeployerProperties {

	/**
	 * Global override for forcing OpenShift Build's of every application
	 */
	private boolean forceBuild;

	/**
	 * See https://docs.openshift.org/latest/architecture/core_concepts/routes.html#route-
	 * hostnames
	 */
	private String defaultRoutingSubdomain = "router.default.svc.cluster.local";

	/**
	 * The default image tag to associate with {@link Build} targets and
	 * {@link DeploymentConfig}'s
	 */
	private String defaultImageTag = "latest";

	/**
	 * Delay in milliseconds to wait for resources to be undeployed.
	 */
	private long undeployDelay = 1000;

	/**
	 * When deploying Maven resource apps, use this provided default Dockerfile. Allowable
	 * values are <code>Dockerfile.artifactory</code> or <code>Dockerfile.nexus</code>.
	 * The Dockerfiles are targeted at the two most common Maven repository distributions,
	 * Nexus and Artifactory, where the API used to download the Maven artifacts is
	 * specific to each distribution. <code>Dockerfile.artifactory</code> is the default
	 * because that is the distribution used by the Spring Maven repository.
	 */
	private String defaultDockerfile = "Dockerfile.artifactory";

	/**
	 * Override an images existing registry with this registry value. This can be used if
	 * the applications image is pushed to the internal registry using the externally
	 * routable address but should be referenced using the internal registry Service.
	 */
	private String dockerRegistryOverride;

	/**
	 * This property is used in conjunction with <code>dockerRegistryOverride</code> and
	 * must be set to the namespace/project name where the server is running. I.e. if the
	 * project where the server is running is called 'my-project', then this value should
	 * be set to 'my-project'. Easiest way to set this is by using the
	 * <code>metadata.namespace</code> value if using a Template.
	 */
	private String imageProjectName;

	/**
	 * The default S2I build image to use for Maven resource. See
	 * https://github.com/fabric8io-images/s2i
	 */
	private String defaultS2iImage = "fabric8/s2i-java:latest-java11";

	private String containerCommand = "/usr/local/s2i/run";

	public boolean isForceBuild() {
		return this.forceBuild;
	}

	public void setForceBuild(boolean forceBuild) {
		this.forceBuild = forceBuild;
	}

	public String getDefaultRoutingSubdomain() {
		return this.defaultRoutingSubdomain;
	}

	public void setDefaultRoutingSubdomain(String defaultRoutingSubdomain) {
		this.defaultRoutingSubdomain = defaultRoutingSubdomain;
	}

	public String getDefaultImageTag() {
		return this.defaultImageTag;
	}

	public void setDefaultImageTag(final String defaultImageTag) {
		this.defaultImageTag = defaultImageTag;
	}

	public String getDefaultImageNamespace() {
		return getNamespace();
	}

	public long getUndeployDelay() {
		return this.undeployDelay;
	}

	public void setUndeployDelay(final long undeployDelay) {
		this.undeployDelay = undeployDelay;
	}

	public String getDefaultDockerfile() {
		return this.defaultDockerfile;
	}

	public void setDefaultDockerfile(final String defaultDockerfile) {
		this.defaultDockerfile = defaultDockerfile;
	}

	public String getDockerRegistryOverride() {
		return this.dockerRegistryOverride;
	}

	public void setDockerRegistryOverride(String dockerRegistryOverride) {
		this.dockerRegistryOverride = dockerRegistryOverride;
	}

	public String getImageProjectName() {
		return this.imageProjectName;
	}

	public void setImageProjectName(String imageProjectName) {
		this.imageProjectName = imageProjectName;
	}

	public String getDefaultS2iImage() {
		return this.defaultS2iImage;
	}

	public void setDefaultS2iImage(String defaultS2iImage) {
		this.defaultS2iImage = defaultS2iImage;
	}

	public String getContainerCommand() {
		return this.containerCommand;
	}

	public void setContainerCommand(String containerCommand) {
		this.containerCommand = containerCommand;
	}

}
