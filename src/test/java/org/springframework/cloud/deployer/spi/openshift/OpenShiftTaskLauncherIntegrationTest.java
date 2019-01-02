package org.springframework.cloud.deployer.spi.openshift;

import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Copied <a href="https://github.com/spring-cloud/spring-cloud-deployer-kubernetes">from
 * spring-cloud-deployer-kubernetes</a> to test the <code>docker:</code> resource
 * handling.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = { OpenShiftTaskLauncherIntegrationTest.Config.class,
		OpenShiftAutoConfiguration.class })
public class OpenShiftTaskLauncherIntegrationTest
		extends AbstractOpenShiftTaskLauncherIntegrationTest {

	@Autowired
	private TaskLauncher taskLauncher;

	@Override
	protected TaskLauncher provideTaskLauncher() {
		return taskLauncher;
	}

	@Override
	protected Resource testApplication() {
		return new DockerResource(
				"springcloud/spring-cloud-deployer-spi-test-app:latest");
	}

}
