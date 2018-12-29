package org.springframework.cloud.deployer.spi.openshift;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.openshift.client.OpenShiftClient;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.kubernetes.ContainerFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.pod.OpenShiftContainerFactory;
import org.springframework.cloud.deployer.spi.openshift.resources.volumes.VolumeMountFactory;
import org.springframework.cloud.deployer.spi.test.AbstractAppDeployerIntegrationTests;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.*;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

/**
 * Copied <a href="https://github.com/spring-cloud/spring-cloud-deployer-kubernetes">from
 * spring-cloud-deployer-kubernetes</a> to test the <code>docker:</code> resource
 * handling.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = { OpenShiftAppDeployerIntegrationTest.Config.class,
		OpenShiftAutoConfiguration.class })
public class OpenShiftAppDeployerIntegrationTest
		extends AbstractAppDeployerIntegrationTests {

	@ClassRule
	public static OpenShiftTestSupport openShiftAvailable = new OpenShiftTestSupport();

	@Autowired
	private AppDeployer appDeployer;

	@Autowired
	private OpenShiftClient openShiftClient;

	@Override
	protected AppDeployer provideAppDeployer() {
		return appDeployer;
	}

	@After
	public void cleanUp() {
		openShiftClient.services().withLabel("spring-app-id").delete();
		openShiftClient.routes().withLabel("spring-app-id").delete();
		openShiftClient.deploymentConfigs().withLabel("spring-app-id").delete();
		openShiftClient.replicationControllers().withLabel("spring-app-id").delete();
		openShiftClient.pods().withLabel("spring-app-id").delete();
		openShiftClient.pods().withLabel("openshift.io/deployer-pod-for.name").delete();
	}

	@Test
	@Override
	@Ignore("See https://github.com/donovanmuller/spring-cloud-deployer-openshift/issues/56")
	public void testApplicationPropertiesPassing() {
		super.testApplicationPropertiesPassing();
	}

	@Test
	@Override
	@Ignore("See https://github.com/donovanmuller/spring-cloud-deployer-openshift/issues/56")
	public void testCommandLineArgumentsPassing() {
		super.testCommandLineArgumentsPassing();
	}

	/**
	 * The test below are copied as is from KubernetesAppDeployerIntegrationTests. See
	 * spring-cloud-deployer-kubernetes project.
	 */

	@Test
	public void testFailedDeploymentWithLoadBalancer() {
		log.info("Testing {}...", "FailedDeploymentWithLoadBalancer");
		OpenShiftDeployerProperties openShiftDeployerProperties = new OpenShiftDeployerProperties();
		openShiftDeployerProperties.setCreateLoadBalancer(true);
		openShiftDeployerProperties.setLivenessProbePeriod(10);
		openShiftDeployerProperties.setMaxTerminatedErrorRestarts(1);
		openShiftDeployerProperties.setMaxCrashLoopBackOffRestarts(1);
		ContainerFactory containerFactory = new OpenShiftContainerFactory(
				openShiftDeployerProperties,
				new VolumeMountFactory(new OpenShiftDeployerProperties()));
		AppDeployer lbAppDeployer = new OpenShiftAppDeployer(openShiftDeployerProperties,
				openShiftClient, containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		Map<String, String> props = new HashMap<>();
		// setting to small memory value will cause app to fail to be deployed
		props.put("spring.cloud.deployer.kubernetes.memory", "8Mi");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = lbAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId,
				eventually(hasStatusThat(Matchers.hasProperty("state", is(failed))),
						timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		lbAppDeployer.undeploy(deploymentId);
		assertThat(deploymentId,
				eventually(hasStatusThat(Matchers.hasProperty("state", is(unknown))),
						timeout.maxAttempts, timeout.pause));
	}

	@Test
	public void testGoodDeploymentWithLoadBalancer() {
		log.info("Testing {}...", "GoodDeploymentWithLoadBalancer");
		OpenShiftDeployerProperties lbProperties = new OpenShiftDeployerProperties();
		lbProperties.setCreateLoadBalancer(true);
		lbProperties.setMinutesToWaitForLoadBalancer(1);
		ContainerFactory containerFactory = new OpenShiftContainerFactory(lbProperties,
				new VolumeMountFactory(new OpenShiftDeployerProperties()));
		AppDeployer lbAppDeployer = new OpenShiftAppDeployer(lbProperties,
				openShiftClient, containerFactory);

		AppDefinition definition = new AppDefinition(randomName(), null);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = lbAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId,
				eventually(hasStatusThat(Matchers.hasProperty("state", is(deployed))),
						timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		lbAppDeployer.undeploy(deploymentId);
		assertThat(deploymentId,
				eventually(hasStatusThat(Matchers.hasProperty("state", is(unknown))),
						timeout.maxAttempts, timeout.pause));
	}

	@Test
	public void testDeploymentWithMountedVolume() {
		log.info("Testing {}...", "DeploymentWithMountedVolume");
		String hostPath = "/tmp/" + randomName() + '/';
		String containerPath = "/tmp/";
		String subPath = randomName();
		String mountName = "mount";
		OpenShiftDeployerProperties openShiftDeployerProperties = new OpenShiftDeployerProperties();
		//@formatter:off
		openShiftDeployerProperties.setVolumes(Collections.singletonList(
			new VolumeBuilder()
				.withName(mountName)
				.build()));
		//@formatter:on
		openShiftDeployerProperties.setVolumeMounts(Collections
				.singletonList(new VolumeMount(hostPath, null, mountName, false, null)));
		ContainerFactory containerFactory = new OpenShiftContainerFactory(
				new OpenShiftDeployerProperties(),
				new VolumeMountFactory(openShiftDeployerProperties));
		AppDeployer lbAppDeployer = new OpenShiftAppDeployer(openShiftDeployerProperties,
				openShiftClient, containerFactory);

		AppDefinition definition = new AppDefinition(randomName(),
				Collections.singletonMap("logging.file", containerPath + subPath));
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = lbAppDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId,
				eventually(hasStatusThat(Matchers.hasProperty("state", is(deployed))),
						timeout.maxAttempts, timeout.pause));

		Map<String, String> selector = Collections.singletonMap("spring-app-id",
				deploymentId);
		PodSpec spec = openShiftClient.pods().withLabels(selector).list().getItems()
				.get(0).getSpec();
		assertThat(spec.getVolumes(), is(notNullValue()));
		//@formatter:off
		Volume volume = spec.getVolumes().stream()
			.filter(v -> mountName.equals(v.getName()))
			.findAny()
			.orElseThrow(() -> new AssertionError("Volume not mounted"));
		//@formatter:on
		assertThat(volume.getHostPath(), is(nullValue()));

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		lbAppDeployer.undeploy(deploymentId);
		assertThat(deploymentId,
				eventually(hasStatusThat(Matchers.hasProperty("state", is(unknown))),
						timeout.maxAttempts, timeout.pause));
	}

	@Test
	public void testDeploymentWithGroupAndIndex() throws IOException {
		log.info("Testing {}...", "DeploymentWithWithGroupAndIndex");

		AppDefinition definition = new AppDefinition(randomName().substring(0, 18),
				new HashMap<>());
		Resource resource = testApplication();
		Map<String, String> props = new HashMap<>();
		props.put(AppDeployer.GROUP_PROPERTY_KEY, "foo");
		props.put(AppDeployer.INDEXED_PROPERTY_KEY, "true");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				props);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = appDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId,
				eventually(hasStatusThat(Matchers.hasProperty("state", is(deployed))),
						timeout.maxAttempts, timeout.pause));

		Map<String, String> selector = Collections.singletonMap("spring-app-id",
				deploymentId);
		PodSpec spec = openShiftClient.pods().withLabels(selector).list().getItems()
				.get(0).getSpec();
		Map<String, String> envVars = new HashMap<>();
		for (EnvVar e : spec.getContainers().get(0).getEnv()) {
			envVars.put(e.getName(), e.getValue());
		}
		assertThat(envVars.get("SPRING_CLOUD_APPLICATION_GROUP"), is("foo"));
		assertThat(envVars.get("SPRING_APPLICATION_INDEX"), is("0"));

		log.info("Undeploying {}...", deploymentId);
		timeout = undeploymentTimeout();
		appDeployer.undeploy(deploymentId);
		assertThat(deploymentId,
				eventually(hasStatusThat(Matchers.hasProperty("state", is(unknown))),
						timeout.maxAttempts, timeout.pause));
	}

	@Override
	protected String randomName() {
		// Kubernetes app names must start with a letter and can only be 24 characters
		return "app-" + super.randomName().substring(0, 18);
	}

	@Override
	protected Timeout deploymentTimeout() {
		return new Timeout(20, 5000);
	}

	@Override
	protected Resource testApplication() {
		return new DockerResource(
				"springcloud/spring-cloud-deployer-spi-test-app:latest");
	}

}
