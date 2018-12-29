package org.springframework.cloud.deployer.spi.openshift;

import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.test.AbstractTaskLauncherIntegrationTests;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = { MavenOpenShiftTaskLauncherIntegrationTest.Config.class,
		OpenShiftAutoConfiguration.class })
@TestPropertySource(properties = {
		"maven.remote-repositories.spring.url=http://repo.spring.io/libs-snapshot" })
public class MavenOpenShiftTaskLauncherIntegrationTest
		extends AbstractTaskLauncherIntegrationTests {

	@ClassRule
	public static OpenShiftTestSupport openShiftTestSupport = new OpenShiftTestSupport();

	@Autowired
	private OpenShiftClient openShiftClient;

	@Autowired
	private ResourceAwareOpenShiftTaskLauncher taskLauncher;

	@Autowired
	private MavenProperties mavenProperties;

	@Override
	protected TaskLauncher provideTaskLauncher() {
		return taskLauncher;
	}

	@Test
	@Override
	@Ignore("Currently reported as completed instead of cancelled")
	public void testSimpleCancel() throws InterruptedException {
		super.testSimpleCancel();
	}

	@Override
	protected String randomName() {
		// Kubernetes app names must start with a letter and can only be 24 characters
		return "task-" + UUID.randomUUID().toString().substring(0, 18);
	}

	@Override
	protected Timeout deploymentTimeout() {
		return new Timeout(20, 5000);
	}

	@Override
	protected Resource testApplication() {
		Properties properties = new Properties();
		try {
			properties.load(new ClassPathResource("integration-test-app.properties")
					.getInputStream());
		}
		catch (IOException e) {
			throw new RuntimeException(
					"Failed to determine which version of integration-test-app to use",
					e);
		}
		return new MavenResource.Builder(mavenProperties)
				.groupId("org.springframework.cloud")
				.artifactId("spring-cloud-deployer-spi-test-app").classifier("exec")
				.version(properties.getProperty("version")).extension("jar").build();
	}

}
