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

package org.springframework.cloud.deployer.spi.openshift.resources.imageStream;

import java.util.Optional;

import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.client.OpenShiftClient;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.resources.AbstractObjectFactory;

/**
 * ImageStream factory.
 *
 * @author Donovan Muller
 */
public class ImageStreamFactory extends AbstractObjectFactory<ImageStream> {

	private OpenShiftClient client;

	public ImageStreamFactory(OpenShiftClient client) {
		this.client = client;
	}

	@Override
	protected ImageStream createObject(AppDeploymentRequest request, String appId) {
		//@formatter:off
		return this.client.imageStreams()
			.createNew()
				.withNewMetadata()
					.withName(appId)
			.endMetadata()
			.done();
		//@formatter:on
	}

	@Override
	public void applyObject(AppDeploymentRequest request, String appId) {
		// do nothing
	}

	@Override
	protected Optional<ImageStream> getExisting(String name) {
		//@formatter:off
		return Optional.ofNullable(this.client.imageStreams()
			.withName(name)
			.fromServer()
			.get());
		//@formatter:on
	}

}
