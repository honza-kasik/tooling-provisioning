import org.wildfly.qa.tooling.featurepack.inspector.ArtifactListFeaturePackInspector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class ArtifactListFeaturePackInspectorTest {

    @Test
    public void testParsingArtifactListTxt() throws URISyntaxException, IOException {
        final ArtifactListFeaturePackInspector artifactListFeaturePackInspector = new ArtifactListFeaturePackInspector(
                Path.of(ArtifactListFeaturePackInspectorTest.class.getClassLoader().getResource("artifact-list.txt").toURI()));

        Assertions.assertEquals("41.0.1.Final",
                artifactListFeaturePackInspector.inspectArtifactVersion("org.wildfly", "wildfly-parent"));
    }

    @Test
    public void testParsingArtifactListTxtNotFound() throws URISyntaxException, IOException {
        final ArtifactListFeaturePackInspector artifactListFeaturePackInspector = new ArtifactListFeaturePackInspector(
                Path.of(ArtifactListFeaturePackInspectorTest.class.getClassLoader().getResource("artifact-list.txt").toURI()));

        Assertions.assertNull(artifactListFeaturePackInspector.inspectArtifactVersion("org.wildfly", "foobar"));
    }

}
