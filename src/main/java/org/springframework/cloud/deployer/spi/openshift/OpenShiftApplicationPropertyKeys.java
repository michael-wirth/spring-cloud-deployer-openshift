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

/**
 * Application key constants for Openshift.
 *
 * @author Donovan Muller
 */
public interface OpenShiftApplicationPropertyKeys {

	/**
	 * The Git remote repository URI that will contain a Dockerfile in src/main/docker.
	 * See https://docs.openshift.org/latest/dev_guide/builds.html#source-code.
	 */
	String OPENSHIFT_BUILD_GIT_URI_PROPERTY = "spring.cloud.deployer.openshift.build.git.uri";

	/**
	 * The Git branch/reference for
	 * {@link OpenShiftApplicationPropertyKeys#OPENSHIFT_BUILD_GIT_URI_PROPERTY}. See
	 * https://docs.openshift.org/latest/dev_guide/builds.html#source-code
	 */
	String OPENSHIFT_BUILD_GIT_REF_PROPERTY = "spring.cloud.deployer.openshift.build.git.ref";

	/**
	 * The location, relative to the project root, where the Dockerfile is located.
	 */
	String OPENSHIFT_BUILD_GIT_DOCKERFILE_PATH = "spring.cloud.deployer.openshift.build.git.dockerfile";

	/**
	 * If the remote Git repository requires a secret. See
	 * https://docs.openshift.org/latest/dev_guide/builds.html#using-secrets
	 */
	String OPENSHIFT_BUILD_GIT_SOURCE_SECRET = "spring.cloud.deployer.openshift.build.git.secret";

}
