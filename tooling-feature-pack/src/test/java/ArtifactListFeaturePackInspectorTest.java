import org.jboss.eap.qe.tooling.featurepack.inspector.ArtifactListFeaturePackInspector;
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

        Assertions.assertEquals("8.0.6.SP1-redhat-00001",
                artifactListFeaturePackInspector.inspectArtifactVersion("org.jboss.eap", "jboss-eap-parent"));
    }

    @Test
    public void testParsingArtifactListTxtNotFound() throws URISyntaxException, IOException {
        final ArtifactListFeaturePackInspector artifactListFeaturePackInspector = new ArtifactListFeaturePackInspector(
                Path.of(ArtifactListFeaturePackInspectorTest.class.getClassLoader().getResource("artifact-list.txt").toURI()));

        Assertions.assertNull(artifactListFeaturePackInspector.inspectArtifactVersion("org.jboss.eap", "foobar"));
    }


}
