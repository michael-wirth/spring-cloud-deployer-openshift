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

import java.util.function.BiConsumer;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

/**
 * Suport class for dataflow.
 *
 * @author Donovan Muller
 */
public interface DataflowSupport {

	default Integer getAppInstanceCount(AppDeploymentRequest request) {
		String countProperty = request.getDeploymentProperties()
				.get(AppDeployer.COUNT_PROPERTY_KEY);
		return (countProperty != null) ? Integer.parseInt(countProperty) : 1;
	}

	default boolean isIndexed(AppDeploymentRequest request) {
		String indexedProperty = request.getDeploymentProperties()
				.get(AppDeployer.INDEXED_PROPERTY_KEY);
		return (indexedProperty != null) ? Boolean.valueOf(indexedProperty) : false;
	}

	default void withIndexedDeployment(String appId, AppDeploymentRequest request,
			BiConsumer<String, AppDeploymentRequest> consumer) {
		if (isIndexed(request)) {
			Integer count = getAppInstanceCount(request);
			for (int index = 0; index < count; index++) {
				String indexedId = appId + "-" + index;
				consumer.accept(indexedId, request);
			}
		}
		else {
			consumer.accept(appId, request);
		}
	}
}
