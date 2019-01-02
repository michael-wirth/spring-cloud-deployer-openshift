package org.springframework.cloud.deployer.spi.openshift;

import org.hamcrest.Matchers;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.cloud.deployer.spi.test.AbstractTaskLauncherIntegrationTests;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertThat;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

/**
 * Copied <a href="https://github.com/spring-cloud/spring-cloud-deployer-kubernetes">from
 * spring-cloud-deployer-kubernetes</a> to test the <code>docker:</code> resource
 * handling.
 */
@ContextConfiguration(classes = {
		AbstractOpenShiftTaskLauncherIntegrationTest.Config.class,
		OpenShiftAutoConfiguration.class })
abstract public class AbstractOpenShiftTaskLauncherIntegrationTest
		extends AbstractTaskLauncherIntegrationTests {

	@ClassRule
	public static OpenShiftTestSupport openShiftTestSupport = new OpenShiftTestSupport();

	/*
	 * Kubernetes/Openshift don'y know cancelled state.
	 *
	 * The Pod will be deleted and does no longer exist on the platform. In this case the
	 * platform correcyly reports "unknown" as the Pod can't be found on the platform.
	 */
	@Override
	@Test
	public void testSimpleCancel() throws InterruptedException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "-1");
		appProperties.put("exitCode", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Launching {}...", request.getDefinition().getName());
		String launchId = taskLauncher().launch(request);

		Timeout timeout = deploymentTimeout();
		assertThat(launchId,
				eventually(
						hasStatusThat(Matchers.hasProperty("state",
								Matchers.is(LaunchState.running))),
						timeout.maxAttempts, timeout.pause));

		log.info("Cancelling {}...", request.getDefinition().getName());
		taskLauncher().cancel(launchId);

		timeout = undeploymentTimeout();
		assertThat(launchId,
				eventually(
						hasStatusThat(Matchers.hasProperty("state",
								Matchers.is(LaunchState.unknown))),
						timeout.maxAttempts, timeout.pause));

		taskLauncher().destroy(definition.getName());
	}

	@Override
	protected String randomName() {
		// Kubernetes app names must start with a letter and can only be 24 characters
		return "task-" + UUID.randomUUID().toString().substring(0, 18);
	}

	@Override
	protected Timeout deploymentTimeout() {
		return new Timeout(36, 5000);
	}

}
