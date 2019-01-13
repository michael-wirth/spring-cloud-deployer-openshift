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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;

import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.core.io.Resource;

/**
 * Given a Maven artifact {@link Resource}, use the POM to build a {@link MavenProject}
 * representation.
 */
public class MavenResourceProjectExtractor {

	public MavenProject extractMavenProject(Resource mavenArtifactResource,
			MavenProperties mavenProperties) throws Exception {
		ContainerConfiguration config = new DefaultContainerConfiguration();
		config.setAutoWiring(true);
		config.setClassPathScanning(PlexusConstants.SCANNING_INDEX);
		PlexusContainer plexusContainer = new DefaultPlexusContainer(config);
		ProjectBuilder projectBuilder = plexusContainer.lookup(ProjectBuilder.class);

		RepositorySystem repositorySystem = plexusContainer
				.lookup(RepositorySystem.class);
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

		// only use the local cache to resolve artifacts
		LocalRepository localRepository = new LocalRepository(
				mavenProperties.getLocalRepository());
		session.setLocalRepositoryManager(
				repositorySystem.newLocalRepositoryManager(session, localRepository));

		DefaultProjectBuildingRequest request = new DefaultProjectBuildingRequest();
		request.setRepositorySession(session);
		request.setResolveDependencies(false);
		request.setSystemProperties(System.getProperties());
		ProjectBuildingResult result = projectBuilder
				.build(toArtifact((MavenResource) mavenArtifactResource), request);

		return result.getProject();
	}

	/**
	 * See
	 * {@link org.springframework.cloud.deployer.resource.maven.MavenArtifactResolver#toArtifact}
	 * @param resource of maven artifact
	 * @return a Maven {@link Artifact}
	 */
	private Artifact toArtifact(MavenResource resource) {
		return new DefaultArtifact(resource.getGroupId(), resource.getArtifactId(),
				resource.getVersion(), Artifact.SCOPE_COMPILE, "jar",
				(resource.getClassifier() != null) ? resource.getClassifier() : "",
				new DefaultArtifactHandler());
	}

}
