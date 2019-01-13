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
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.openshift.maven.MavenOpenShiftTaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;

/**
 * This TaskLauncher handles handles Maven-, Project- and Dockerresources.
 *
 * @author Donovan Muller
 */
public class ResourceAwareOpenShiftTaskLauncher implements TaskLauncher {

	private static final Logger logger = LoggerFactory
			.getLogger(ResourceAwareOpenShiftTaskLauncher.class);

	private OpenShiftTaskLauncher openShiftTaskLauncher;

	private MavenOpenShiftTaskLauncher mavenOpenShiftTaskLauncher;

	public ResourceAwareOpenShiftTaskLauncher(OpenShiftTaskLauncher openShiftTaskLauncher,
			MavenOpenShiftTaskLauncher mavenOpenShiftTaskLauncher) {
		this.openShiftTaskLauncher = openShiftTaskLauncher;
		this.mavenOpenShiftTaskLauncher = mavenOpenShiftTaskLauncher;
	}

	@Override
	public String launch(AppDeploymentRequest request) {
		String taskId;

		try {
			if (request.getResource() instanceof MavenResource) {
				taskId = this.mavenOpenShiftTaskLauncher.launch(request);
			}
			else {
				taskId = this.openShiftTaskLauncher.launch(request);
			}
		}
		catch (Exception ex) {
			logger.error(String.format(
					"Error deploying application deployment request: %s", request), ex);
			throw ex;
		}

		return taskId;
	}

	@Override
	public void cancel(String taskId) {
		this.openShiftTaskLauncher.cancel(taskId);
	}

	@Override
	public TaskStatus status(String taskId) {
		return this.openShiftTaskLauncher.status(taskId);
	}

	@Override
	public void cleanup(final String taskId) {
		this.openShiftTaskLauncher.cancel(taskId);
	}

	@Override
	public void destroy(final String taskId) {
		this.openShiftTaskLauncher.destroy(taskId);
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return this.openShiftTaskLauncher.environmentInfo();
	}

}
