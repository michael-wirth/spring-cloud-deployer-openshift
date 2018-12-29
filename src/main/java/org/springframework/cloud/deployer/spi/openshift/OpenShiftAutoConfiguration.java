package org.springframework.cloud.deployer.spi.openshift;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.kubernetes.ContainerFactory;
import org.springframework.cloud.deployer.spi.openshift.maven.MavenOpenShiftAppDeployer;
import org.springframework.cloud.deployer.spi.openshift.maven.MavenOpenShiftTaskLauncher;
import org.springframework.cloud.deployer.spi.openshift.maven.MavenResourceJarExtractor;
import org.springframework.cloud.deployer.spi.openshift.resources.pod.OpenShiftContainerFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.volumes.VolumeMountConfigServerFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.volumes.VolumeMountFactory;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring Bean configuration for the OpenShift deployer.
 *
 * @author Donovan Muller
 */
@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(OpenShiftDeployerProperties.class)
public class OpenShiftAutoConfiguration {

	@Autowired
	private MavenProperties mavenProperties;

	@Bean
	public AppDeployer appDeployer(OpenShiftDeployerProperties properties,
			KubernetesClient kubernetesClient, ContainerFactory containerFactory,
			MavenResourceJarExtractor mavenResourceJarExtractor,
			ResourceHash resourceHash) {

		return new ResourceAwareOpenShiftAppDeployer(
				new OpenShiftAppDeployer(properties, kubernetesClient, containerFactory),
				new MavenOpenShiftAppDeployer(properties, kubernetesClient,
						containerFactory, mavenResourceJarExtractor, mavenProperties,
						resourceHash));
	}

	@Bean
	public TaskLauncher taskDeployer(OpenShiftDeployerProperties properties,
			KubernetesClient kubernetesClient, ContainerFactory containerFactory,
			MavenResourceJarExtractor mavenResourceJarExtractor,
			ResourceHash resourceHash) {
		return new ResourceAwareOpenShiftTaskLauncher(
				new OpenShiftTaskLauncher(properties, kubernetesClient, containerFactory),
				new MavenOpenShiftTaskLauncher(properties, properties, mavenProperties,
						kubernetesClient, mavenResourceJarExtractor, resourceHash,
						containerFactory));
	}

	@Bean
	public KubernetesClient kubernetesClient(OpenShiftDeployerProperties properties) {
		return new DefaultOpenShiftClient().inNamespace(properties.getNamespace());
	}

	@Bean
	public ContainerFactory containerFactory(OpenShiftDeployerProperties properties,
			VolumeMountFactory volumeMountFactory) {
		return new OpenShiftContainerFactory(properties, volumeMountFactory);
	}

	@Bean
	public MavenResourceJarExtractor mavenResourceJarExtractor() {
		return new MavenResourceJarExtractor();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConfigurationProperties(prefix = "maven")
	public MavenProperties mavenProperties() {
		return new MavenProperties();
	}

	@Bean
	public ResourceHash resourceHash() {
		return new ResourceHash();
	}

	@Bean
	@ConditionalOnMissingBean(ConfigServicePropertySourceLocator.class)
	public VolumeMountFactory volumeMountFactory(
			OpenShiftDeployerProperties openShiftDeployerProperties) {
		return new VolumeMountFactory(openShiftDeployerProperties);
	}

	@Bean
	@ConditionalOnBean(ConfigServicePropertySourceLocator.class)
	public VolumeMountFactory volumeMountConfigServiceFactory(
			ConfigServicePropertySourceLocator configServicePropertySourceLocator,
			OpenShiftDeployerProperties openShiftDeployerProperties) {
		return new VolumeMountConfigServerFactory(configServicePropertySourceLocator,
				openShiftDeployerProperties);
	}

}
