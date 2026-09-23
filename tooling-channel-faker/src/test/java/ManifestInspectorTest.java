import org.wildfly.qa.tooling.channel.ChannelManifestInspector;
import org.wildfly.qa.tooling.channel.ChannelManifestLocalRepoReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ChannelManifestInspector}
 */
public class ManifestInspectorTest {

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\t", "\r", "  ", ""})
    @NullSource
    public void testInvalidChannelManifestArtifactId(String artifactId) {
        Exception e = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelManifestInspector("some.valid.id", artifactId, Path.of("some-path")),
                "Exception should be thrown when artifact ID is null");

        assertTrue(e.getMessage().contains("Channel manifest artifact ID must be set!"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\t", "\r", "  ", ""})
    @NullSource
    public void testInvalidChannelManifestGroupId(String groupId) {
        Exception e = assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelManifestInspector(groupId, "artifact-id", Path.of("some-path")),
                "Exception should be thrown when group ID is null");

        assertTrue(e.getMessage().contains("Channel manifest group ID must be set!"));
    }

    @Test
    public void testInstantiationValidValues() {
        new ChannelManifestInspector("some.valid.id", "artifact-id", Path.of("some-path"));
    }

    @Test
    public void testInstantiationWithReader() {
        new ChannelManifestInspector("some.valid.id", "artifactid", new ChannelManifestLocalRepoReader(Path.of("some-path")));
    }
}
