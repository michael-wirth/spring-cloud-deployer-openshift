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

package org.springframework.cloud.deployer.spi.openshift.resources.volumes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Volume;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeploymentPropertyKeys;
import org.springframework.cloud.deployer.spi.openshift.resources.ObjectFactory;

/**
 * Use the Fabric8 {@link Volume} model to allow all volume plugins currently supported.
 * Volume deployment properties are specified in YAML format:
 *
 * <code>
 * spring.cloud.deployer.openshift.volumes=[{name: testhostpath, hostPath: { path: '/test/override/hostPath' }},
 * {name: 'testpvc', persistentVolumeClaim: { claimName: 'testClaim', readOnly: 'true' }},
 * {name: 'testnfs', nfs: { server: '10.0.0.1:111', path: '/test/nfs' }}]
 * </code>
 * <p>
 * Volumes can be specified as deployer properties as well as app deployment properties.
 * Deployment properties override deployer properties.
 */
public class VolumeFactory implements ObjectFactory<List<Volume>> {

	private OpenShiftDeployerProperties properties;

	public VolumeFactory(OpenShiftDeployerProperties properties) {
		this.properties = properties;
	}

	@Override
	public List<Volume> addObject(AppDeploymentRequest request, String appId) {
		Set<Volume> volumes = new LinkedHashSet<>();
		volumes.addAll(getVolumes(request));
		return new ArrayList<>(volumes);
	}

	@Override
	public void applyObject(AppDeploymentRequest request, String appId) {
		// this object cannot be applied by itself
	}

	private List<Volume> getVolumes(AppDeploymentRequest request) {
		List<Volume> volumes = new ArrayList<>();

		String volumeDeploymentProperty = request.getDeploymentProperties().getOrDefault(
				OpenShiftDeploymentPropertyKeys.OPENSHIFT_DEPLOYMENT_VOLUMES,
				StringUtils.EMPTY);
		if (!org.springframework.util.StringUtils.isEmpty(volumeDeploymentProperty)) {
			try {
				KubernetesDeployerProperties kubernetesDeployerProperties = new Yaml()
						.loadAs("{ volumes: " + volumeDeploymentProperty + " }",
								KubernetesDeployerProperties.class);
				volumes.addAll(kubernetesDeployerProperties.getVolumes());
			}
			catch (Exception ex) {
				throw new IllegalArgumentException(
						String.format("Invalid volume '%s'", volumeDeploymentProperty),
						ex);
			}
		}
		// only add volumes that have not already been added, based on the volume's name
		// i.e. allow provided deployment volumes to override deployer defined volumes
		volumes.addAll(this.properties
				.getVolumes().stream().filter(
						(volume) -> volumes.stream()
								.noneMatch((existingVolume) -> existingVolume.getName()
										.equals(volume.getName())))
				.collect(Collectors.toList()));

		return volumes;
	}

}
