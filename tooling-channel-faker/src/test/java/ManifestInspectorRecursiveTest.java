import org.apache.maven.shared.invoker.DefaultInvoker;
import org.wildfly.qa.tooling.channel.ChannelManifestInspector;
import org.wildfly.qa.tooling.channel.ChannelManifestLocalRepoReader;
import org.wildfly.qa.tooling.mavenfaker.ArtifactInstallException;
import org.wildfly.qa.tooling.mavenfaker.MavenArtifactFaker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ManifestInspectorRecursiveTest {

    private static final String MANIFEST_GROUP_ID = "org.wildfly.channels";
    private static final String MANIFEST_ARTIFACT_ID = "wildfly";
    private static final String MANIFEST_REQUIREMENT_ARTIFACT_ID = "wildfly-ee";
    private static final String MANIFEST_VERSION = "35.0.0.Final";

    private static Path tempRepo;

    @BeforeAll
    public static void setupManifestRepository() throws IOException, InterruptedException, URISyntaxException, ArtifactInstallException {
        tempRepo = Files.createTempDirectory("recursive-repo-test");

        //install main manifest
        new MavenArtifactFaker.Builder()
                .groupId(MANIFEST_GROUP_ID)
                .artifactId(MANIFEST_ARTIFACT_ID)
                .version(MANIFEST_VERSION)
                .packaging("yaml")
                .classifier("manifest")
                .mavenRepoLocal(tempRepo.toAbsolutePath().toString())
                .build()
                .installRealFakeArtifactInLocalMavenRepo(new DefaultInvoker().getMavenHome(),
                        Path.of(ManifestInspectorRecursiveTest.class.getClassLoader().getResource("manifest-requirement/wildfly-35.0.0.Final-manifest.yaml").toURI()));

        //install requirement manifest
        new MavenArtifactFaker.Builder()
                .groupId(MANIFEST_GROUP_ID)
                .artifactId(MANIFEST_REQUIREMENT_ARTIFACT_ID)
                .version(MANIFEST_VERSION)
                .packaging("yaml")
                .classifier("manifest")
                .mavenRepoLocal(tempRepo.toAbsolutePath().toString())
                .build()
                .installRealFakeArtifactInLocalMavenRepo(new DefaultInvoker().getMavenHome(),
                        Path.of(ManifestInspectorRecursiveTest.class.getClassLoader().getResource("manifest-requirement/wildfly-ee-35.0.0.Final-manifest.yaml").toURI()));

    }

    @Test
    public void testRecursiveInspectionsInMain() {
        final String version = new ChannelManifestInspector(MANIFEST_GROUP_ID, MANIFEST_ARTIFACT_ID, new ChannelManifestLocalRepoReader(tempRepo))
                .inspectStreamVersion("com.fasterxml.jackson.jr", "jackson-jr-objects", true);
        Assertions.assertEquals("2.18.2", version);
    }

    @Test
    public void testRecursiveInspectionsInRequirement() {
        final String version = new ChannelManifestInspector(MANIFEST_GROUP_ID, MANIFEST_ARTIFACT_ID, new ChannelManifestLocalRepoReader(tempRepo))
                .inspectStreamVersion("com.carrotsearch", "hppc", true);
        Assertions.assertEquals("0.10.0", version);
    }

    @Test
    public void testNonRecursiveInspectionsInRequirement() {
        final String version = new ChannelManifestInspector(MANIFEST_GROUP_ID, MANIFEST_ARTIFACT_ID, new ChannelManifestLocalRepoReader(tempRepo))
                .inspectStreamVersion("com.carrotsearch", "hppc", false);
        Assertions.assertNull(version);
    }
}
