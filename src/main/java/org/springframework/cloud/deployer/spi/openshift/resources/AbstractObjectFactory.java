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

package org.springframework.cloud.deployer.spi.openshift.resources;

import java.util.Optional;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

/**
 * Abstrat class for Openshift objects creation.
 *
 * @author Donovan Muller
 */
public abstract class AbstractObjectFactory<T> implements ObjectFactory {

	@Override
	public T addObject(AppDeploymentRequest request, String appId) {
		return getExisting(appId).orElseGet(() -> createObject(request, appId));
	}

	protected abstract T createObject(AppDeploymentRequest request, String appId);

	protected abstract Optional<T> getExisting(String name);

}
