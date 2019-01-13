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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.openshift.maven.MavenOpenShiftAppDeployer;

/**
 * This AppDeployer handles handles Maven-, Project- and Dockerresources.
 *
 * @author Donovan Muller
 */
public class ResourceAwareOpenShiftAppDeployer implements AppDeployer {

	private static final Logger logger = LoggerFactory
			.getLogger(ResourceAwareOpenShiftAppDeployer.class);

	private OpenShiftAppDeployer openShiftAppDeployer;

	private MavenOpenShiftAppDeployer mavenOpenShiftAppDeployer;

	public ResourceAwareOpenShiftAppDeployer(OpenShiftAppDeployer openShiftAppDeployer,
			MavenOpenShiftAppDeployer mavenOpenShiftAppDeployer) {
		this.openShiftAppDeployer = openShiftAppDeployer;
		this.mavenOpenShiftAppDeployer = mavenOpenShiftAppDeployer;
	}

	@Override
	public String deploy(AppDeploymentRequest request) {
		String appId;

		try {
			if (request.getResource() instanceof MavenResource) {
				appId = this.mavenOpenShiftAppDeployer.deploy(request);
			}
			else {
				appId = this.openShiftAppDeployer.deploy(request);
			}
		}
		catch (Exception ex) {
			logger.error(String.format(
					"Error deploying application deployment request: %s", request), ex);
			throw ex;
		}

		return appId;
	}

	@Override
	public void undeploy(String appId) {
		this.openShiftAppDeployer.undeploy(appId);
	}

	@Override
	public AppStatus status(String appId) {
		return this.openShiftAppDeployer.status(appId);
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return this.openShiftAppDeployer.environmentInfo();
	}

}
