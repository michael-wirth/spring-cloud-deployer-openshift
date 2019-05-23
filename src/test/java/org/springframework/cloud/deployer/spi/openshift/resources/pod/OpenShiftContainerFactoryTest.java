package org.springframework.cloud.deployer.spi.openshift.resources.pod;

import io.fabric8.kubernetes.api.model.Container;
import org.junit.Test;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.ContainerConfiguration;
import org.springframework.cloud.deployer.spi.openshift.OpenShiftDeployerProperties;
import org.springframework.cloud.deployer.spi.openshift.resources.volumes.VolumeMountFactory;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public class OpenShiftContainerFactoryTest {

	@Test
	public void createWithMavenResource() {
		OpenShiftDeployerProperties properties = new OpenShiftDeployerProperties();
		OpenShiftContainerFactory containerFactory = new OpenShiftContainerFactory(
				properties, new VolumeMountFactory(properties));

		AppDefinition definition = new AppDefinition("app-test", null);
		Resource resource = getResource();

		AppDeploymentRequest appDeploymentRequestShell = new AppDeploymentRequest(
				definition, resource, null);
		ContainerConfiguration containerConfiguration = new ContainerConfiguration(
				"app-test", appDeploymentRequestShell);
		Container container = containerFactory.create(containerConfiguration);

		assertNotNull(container);
		assertThat(container.getImage()).isEqualTo("app-test");
	}

	private Resource getResource() {
		return new MavenResource.Builder().groupId("test.com").artifactId("test")
				.version("1.0-SNAPSHOT").build();
	}

}
