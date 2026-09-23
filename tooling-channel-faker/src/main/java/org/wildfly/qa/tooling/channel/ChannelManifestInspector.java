package org.wildfly.qa.tooling.channel;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ManifestRequirement;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility class gathering boilerplate code to inspect channel manifest
 */
public class ChannelManifestInspector {

    private final String channelManifestGroupId;
    private final String channelManifestArtifactId;

    private final ChannelManifestReader channelManifestReader;

    private ChannelManifest channelManifest;

    /**
     * Constructor for ChannelManifestInspector reading the manifest from a local repository path
     * @param channelManifestGroupId GroupId
     * @param channelManifestArtifactId ArtifactId
     * @param mavenRepositoryPath Path to channel manifest in the local filesystem
     */
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
        this.channelManifestReader = new ChannelManifestLocalRepoReader(mavenRepositoryPath);
    }

    /**
     * Constructor for ChannelManifestInspector
     * @param channelManifestReader reader to read the manifest
     */
    public ChannelManifestInspector(final String channelManifestGroupId, final String channelManifestArtifactId,
                                    final ChannelManifestReader channelManifestReader) {
        if (channelManifestArtifactId == null || channelManifestArtifactId.isBlank()) {
            throw new IllegalArgumentException("Channel manifest artifact ID must be set!");
        }
        if (channelManifestGroupId == null || channelManifestGroupId.isBlank()) {
            throw new IllegalArgumentException("Channel manifest group ID must be set!");
        }
        if (channelManifestReader == null) {
            throw new IllegalArgumentException("Channel manifest reader must be set!");
        }
        this.channelManifestGroupId = channelManifestGroupId;
        this.channelManifestArtifactId = channelManifestArtifactId;
        this.channelManifestReader = channelManifestReader;
    }

    /**
     * Find stream version
     * @param groupId group ID of stream
     * @param artifactId artifact ID of stream
     * @return stream version or null if stream is not found in the channel manifest
     */
    public String inspectStreamVersion(final String groupId, final String artifactId) {
        return inspectStreamVersion(groupId, artifactId, false);
    }

    /**
     * Find stream version
     * @param groupId group ID of stream
     * @param artifactId artifact ID of stream
     * @param recursive whether required manifests should be inspected as well
     * @return stream version or null if stream is not found in the channel manifest
     */
    public String inspectStreamVersion(final String groupId, final String artifactId, boolean recursive) {
        final Optional<org.wildfly.channel.Stream> streamOptional = getChannelManifest().findStreamFor(groupId, artifactId);
        if (streamOptional.isPresent()) {
            return streamOptional.map(org.wildfly.channel.Stream::getVersion).get();
        } else if (recursive) {
            return getChannelManifest().getManifestRequirements().stream()
                    .map(requirement -> new ChannelManifestInspector(requirement.getGroupId(),
                            requirement.getArtifactId(),
                            this.channelManifestReader).inspectStreamVersion(groupId, artifactId, true))
                    .filter(Objects::nonNull)
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } else {
            return null;
        }
    }

    /**
     * Find a stream containing string
     * @param string a string which will be matched in the stream artifact ID or group ID
     * @return a stream or null if not found
     */
    public Stream findStreamContaining(final String string) {
        return getChannelManifest().getStreams().stream()
                .filter(stream -> stream.getGroupId().contains(string) || stream.getArtifactId().contains(string))
                .findFirst().
                orElse(null);
    }

    /**
     * Gets the {@link ChannelManifest}
     * @return {@link ChannelManifest}
     */
    private ChannelManifest getChannelManifest() {
        if (this.channelManifest == null) {
            try {
                this.channelManifest = this.channelManifestReader
                        .readChannelManifest(this.channelManifestGroupId, this.channelManifestArtifactId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read the manifest " + this.channelManifestGroupId + ":" +
                        this.channelManifestArtifactId + " using " + this.channelManifestReader, e);
            }
        }
        return this.channelManifest;
    }
}
