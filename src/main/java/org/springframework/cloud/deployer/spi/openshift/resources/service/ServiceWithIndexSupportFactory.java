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

package org.springframework.cloud.deployer.spi.openshift.resources.service;

import java.util.Map;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.client.OpenShiftClient;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.DataflowSupport;

public class ServiceWithIndexSupportFactory extends ServiceFactory
		implements DataflowSupport {

	public ServiceWithIndexSupportFactory(OpenShiftClient client, Integer port,
			Map<String, String> labels) {
		super(client, port, labels);
	}

	@Override
	public Service addObject(AppDeploymentRequest request, String appId) {
		if (isIndexed(request)) {
			for (int index = 0; index < getAppInstanceCount(request); index++) {
				String indexedId = appId + "-" + index;
				super.addObject(request, indexedId);
			}
		}
		else {
			super.addObject(request, appId);
		}

		return null;
	}

	@Override
	protected Service build(AppDeploymentRequest request, String appId, Integer port,
			Map<String, String> labels) {
		labels.replace("spring-deployment-id", appId);
		return super.build(request, appId, port, labels);
	}

}
