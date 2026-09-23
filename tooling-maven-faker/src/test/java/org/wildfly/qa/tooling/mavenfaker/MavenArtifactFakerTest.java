package org.wildfly.qa.tooling.mavenfaker;

import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MavenArtifactFakerTest {

	@Test
	public void test() throws Exception {
		Path mavenRepository = Files.createTempDirectory("maven-repository");
		Path tmpInput = Files.createTempDirectory("tmpInput");
		File artifact = new File(tmpInput.toFile(), "some-fake-artifact.yaml");
		FileUtils.writeStringToFile(artifact, "---", StandardCharsets.UTF_8);
		new MavenArtifactFaker.Builder()
				.mavenRepoLocal(mavenRepository.toFile().getAbsolutePath())
				.groupId("org.jboss.qe.test")
				.artifactId("super-fake-artifact")
				.version("1.0.0-SNAPSHOT")
				.packaging("zip")
				.classifier("manifest")
				.build()
				.installRealFakeArtifactInLocalMavenRepo(new DefaultInvoker().getMavenHome(), artifact.toPath());
		Assertions.assertTrue(mavenRepository
				.resolve("org")
				.resolve("jboss")
				.resolve("qe")
				.resolve("test")
				.resolve("super-fake-artifact")
				.resolve("1.0.0-SNAPSHOT").toFile().exists());
	}
}
