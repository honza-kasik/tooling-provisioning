package org.jboss.eap.qe.tooling.channel;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility class gathering boilerplate code to inspect channel manifest
 */
public class ChannelManifestInspector {

    private final String channelManifestGroupId;
    private final String channelManifestArtifactId;
    private final Path mavenRepositoryPath;

    private ChannelManifest channelManifest;

    public ChannelManifestInspector(final String channelManifestGroupId, final String channelManifestArtifactId,
                                    final Path mavenRepositoryPath) {
        if (channelManifestArtifactId == null || channelManifestArtifactId.isBlank()) {
            throw new IllegalArgumentException("Channel manifest artifact ID must be set!");
        }
        if (channelManifestGroupId == null || channelManifestGroupId.isBlank()) {
            throw new IllegalArgumentException("Channel manifest group ID must be set!");
        }
        this.channelManifestGroupId = channelManifestGroupId;
        this.channelManifestArtifactId = channelManifestArtifactId;
        this.mavenRepositoryPath = mavenRepositoryPath;
    }

    /**
     * Find stream version
     * @param groupId group ID of stream
     * @param artifactId artifact ID of stream
     * @return stream version or null if stream is not found in the channel manifest
     */
    public String inspectStreamVersion(final String groupId, final String artifactId) throws IOException {
        final Optional<org.wildfly.channel.Stream> streamOptional = getChannelManifest().findStreamFor(groupId, artifactId);
        return streamOptional.map(org.wildfly.channel.Stream::getVersion).orElse(null);
    }

    /**
     * Find a stream containing string
     * @param string a string which will be matched in the stream artifact ID or group ID
     * @return a stream or null if not found
     */
    public Stream findStreamContaining(final String string) throws IOException {
        return getChannelManifest().getStreams().stream()
                .filter(stream -> stream.getGroupId().contains(string) || stream.getArtifactId().contains(string))
                .findFirst().
                orElse(null);
    }

    private ChannelManifest getChannelManifest() throws IOException {
        if (this.channelManifest == null) {
            ChannelManifestLocalRepoReader channelManifestLocalRepoReader = new ChannelManifestLocalRepoReader(
                    this.channelManifestGroupId, this.channelManifestArtifactId, this.mavenRepositoryPath
            );
            this.channelManifest = channelManifestLocalRepoReader.readChannelManifest();
        }
        return this.channelManifest;
    }
}
