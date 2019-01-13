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

package org.springframework.cloud.deployer.spi.openshift.resources.route;

import java.util.Map;
import java.util.Optional;

import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeploymentPropertyKeys;
import org.springframework.cloud.deployer.spi.openshift.resources.ObjectFactory;

/**
 * Route factory.
 *
 * @author Donovan Muller
 */
public class RouteFactory implements ObjectFactory<Route> {

	private OpenShiftClient client;

	private OpenShiftDeployerProperties openShiftDeployerProperties;

	private Integer port;

	private Map<String, String> labels;

	public RouteFactory(OpenShiftClient client,
			OpenShiftDeployerProperties openShiftDeployerProperties, Integer port,
			Map<String, String> labels) {
		this.client = client;
		this.openShiftDeployerProperties = openShiftDeployerProperties;
		this.port = port;
		this.labels = labels;
	}

	@Override
	public Route addObject(AppDeploymentRequest request, String appId) {
		Route route = build(request, appId, this.port, this.labels);

		if (getExisting(appId).isPresent()) {
			route = this.client.routes().createOrReplace(route);
		}
		else {
			route = this.client.routes().create(route);
		}

		return route;
	}

	@Override
	public void applyObject(AppDeploymentRequest request, String appId) {
		// do nothing
	}

	protected Optional<Route> getExisting(String name) {
		return Optional.ofNullable(this.client.routes().withName(name).fromServer().get());
	}

	protected Route build(AppDeploymentRequest request, String appId, Integer port,
			Map<String, String> labels) {
		String serviceNameOrAppId = request.getDeploymentProperties().getOrDefault(
				OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_SERVICE_NAME, appId);

		//@formatter:off
		return new RouteBuilder()
			.withNewMetadata()
				.withName(serviceNameOrAppId)
				.withLabels(labels)
			.endMetadata()
			.withNewSpec()
				.withHost(buildHost(request, serviceNameOrAppId))
				.withNewTo()
					.withName(serviceNameOrAppId)
					.withKind("Service")
				.endTo()
				.withNewPort()
					.withNewTargetPort(port)
				.endPort()
			.endSpec()
			.build();
		//@formatter:on
	}

	/**
	 * Builds the <code>host</code> value for a Route. If there is a
	 * <code>spring.cloud.deployer.openshift.deployment.route.hostname</code> deployment
	 * variable with a value, this will be used as the <code>host</code> value for the
	 * Route (see
	 * https://docs.openshift.com/container-platform/latest/architecture/networking/routes.html#route-hostnames)
	 * Alternatively, the <code>host</code> value is built up using:
	 *
	 * <ul>
	 * <li>application Id</li>
	 * <li>the namespace currently connected too</li>
	 * <li>the configured default routing subdomain - see
	 * https://docs.openshift.com/container-platform/latest/install_config/router/default_haproxy_router.html#customizing-the-default-routing-subdomain</li>
	 * </ul>
	 *
	 * The format of the Route host is built as follows:
	 *
	 * <p>
	 * <code>{application}-{namespace}-{default routing subdomain}</code>
	 * </p>
	 *
	 * See
	 * https://docs.openshift.com/container-platform/latest/architecture/networking/routes.html
	 * @param request application deployment
	 * @param appId of application deployment
	 * @return host value for the Route
	 */
	protected String buildHost(AppDeploymentRequest request, String appId) {
		return request.getDeploymentProperties().getOrDefault(
				OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_ROUTE_HOSTNAME,
				String.format("%s-%s.%s", appId, this.client.getNamespace(),
						this.openShiftDeployerProperties.getDefaultRoutingSubdomain()));
	}

}
