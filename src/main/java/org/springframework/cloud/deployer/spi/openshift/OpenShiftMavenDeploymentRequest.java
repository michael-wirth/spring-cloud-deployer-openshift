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

import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.maven.GitReference;
import org.springframework.cloud.deployer.spi.openshift.maven.MavenResourceProjectExtractor;

/**
 * Deployment request from project source.
 *
 * @author Donovan Muller
 */
public class OpenShiftMavenDeploymentRequest extends AppDeploymentRequest {

	private static final Logger logger = LoggerFactory
			.getLogger(OpenShiftMavenDeploymentRequest.class);

	private GitReference gitReference;

	/**
	 * The {@link org.springframework.cloud.deployer.resource.maven.MavenResource} is used
	 * to parse and build a {@link MavenProject}. This is used to get the SCM details for
	 * use with the different build strategies.
	 * @param request app deployment specification
	 * @param mavenResourceProjectExtractor maven project extractor
	 * @param mavenProperties maven configuration properties
	 * @throws IllegalStateException if the {@link MavenProject} cannot be parsed from the
	 * {@link org.springframework.cloud.deployer.resource.maven.MavenResource}
	 */
	public OpenShiftMavenDeploymentRequest(AppDeploymentRequest request,
			MavenResourceProjectExtractor mavenResourceProjectExtractor,
			MavenProperties mavenProperties) {
		super(request.getDefinition(), request.getResource(),
				request.getDeploymentProperties(), request.getCommandlineArguments());

		try {
			MavenProject mavenProject = mavenResourceProjectExtractor
					.extractMavenProject(this.getResource(), mavenProperties);
			Scm scm = mavenProject.getScm();
			this.gitReference = new GitReference(scm.getConnection(), scm.getTag());
		}
		catch (Exception ex) {
			logger.warn(String.format(
					"Maven project could not be extracted. Maven resource [%s] possibly has no pom extension artifact",
					getResource()), ex);
		}
	}

	public OpenShiftMavenDeploymentRequest(AppDeploymentRequest request,
			MavenProperties mavenProperties) {
		this(request, new MavenResourceProjectExtractor(), mavenProperties);
	}

	public boolean isMavenProjectExtractable() {
		return this.gitReference != null;
	}

	public GitReference getGitReference() {
		return this.gitReference;
	}

}
