import org.apache.maven.shared.invoker.DefaultInvoker;
import org.wildfly.qa.tooling.channel.ChannelManifestLocalRepoReader;
import org.wildfly.qa.tooling.channel.ChannelManifestPress;
import org.wildfly.qa.tooling.channel.ChannelManifestReader;
import org.wildfly.qa.tooling.mavenfaker.ArtifactInstallException;
import org.wildfly.qa.tooling.mavenfaker.MavenArtifactFaker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.ChannelManifest;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChannelManifestPressTest {

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
    public void testFlattenManifests() throws IOException {
        final ChannelManifestReader channelManifestReader = new ChannelManifestLocalRepoReader(tempRepo);
        final ChannelManifest topLevelManifest = channelManifestReader.readChannelManifest(MANIFEST_GROUP_ID, MANIFEST_ARTIFACT_ID, MANIFEST_VERSION);
        final ChannelManifest requiredManifest = channelManifestReader.readChannelManifest(MANIFEST_GROUP_ID, MANIFEST_REQUIREMENT_ARTIFACT_ID, MANIFEST_VERSION);

        final ChannelManifestPress channelManifestPress = new ChannelManifestPress(topLevelManifest);
        final ChannelManifest flattenedManifest = channelManifestPress.flatten(channelManifestReader);

        //metadata
        Assertions.assertEquals(topLevelManifest.getId(), flattenedManifest.getId());
        Assertions.assertEquals(topLevelManifest.getDescription(), flattenedManifest.getDescription());
        Assertions.assertEquals(topLevelManifest.getLogicalVersion(), flattenedManifest.getLogicalVersion());
        Assertions.assertEquals(topLevelManifest.getName(), flattenedManifest.getName());

        //stream comparison
        Assertions.assertTrue(flattenedManifest.getStreams().containsAll(topLevelManifest.getStreams()));
        Assertions.assertTrue(flattenedManifest.getStreams().containsAll(requiredManifest.getStreams()));
    }

}
