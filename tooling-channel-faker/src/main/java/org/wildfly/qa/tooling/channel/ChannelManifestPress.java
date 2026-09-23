package org.wildfly.qa.tooling.channel;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool to compose "effective" one-level manifest when manifest contains any other references
 * in the "required" section.
 * Called "press" since it flattens the manifest.
 */
public class ChannelManifestPress {

    final ChannelManifest channelManifest;

    public ChannelManifestPress(ChannelManifest channelManifest) {
        this.channelManifest = channelManifest;
    }

    /**
     * Flatten the manifest to one level. The resulting manifest will have all top-level manifest metadata.
     * Obviously, the flattened manifest won't contain any requirements.
     * @param channelManifestReader channel manifest reader to use to read required manifests
     * @return flattened manifest or a clone of the original manifest if no requirements are defined
     */
    public ChannelManifest flatten(ChannelManifestReader channelManifestReader) {
        final Set<Stream> requiredStreams = readRequiredStreams(this.channelManifest, channelManifestReader);
        final Collection<Stream> topLevelStreams = this.channelManifest.getStreams();
        final Set<Stream> allStreams = new HashSet<>();

        allStreams.addAll(requiredStreams);
        allStreams.addAll(topLevelStreams);

        return new ChannelManifest(
                this.channelManifest.getName(),
                this.channelManifest.getId(),
                this.channelManifest.getLogicalVersion(),
                this.channelManifest.getDescription(),
                Collections.emptyList(),
                allStreams);
    }

    private Set<Stream> readRequiredStreams(ChannelManifest channelManifest, ChannelManifestReader channelManifestReader) {
        return channelManifest.getManifestRequirements()
                .stream()
                .map(requirement -> {
                    try {
                        return channelManifestReader.readChannelManifest(requirement.getGroupId(), requirement.getArtifactId());
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to read manifest " + requirement.getGroupId() + ":" +
                                requirement.getArtifactId() + " using " + channelManifestReader, e);
                    }
                })
                .map(ChannelManifest::getStreams)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

}
