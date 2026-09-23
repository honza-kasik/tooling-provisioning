package org.wildfly.qa.tooling.channel;

import org.wildfly.channel.ChannelManifest;
import java.io.IOException;

/**
 * Reads a Channel Manifest
 */
public interface ChannelManifestReader {

	/**
	 * Reads a Channel Manifest in a {@link org.wildfly.channel.ChannelManifest} 
	 *
	 * @return {@link org.wildfly.channel.ChannelManifest}
	 * @throws IOException in case it resolves multiple Channel Manifests
	 */
	ChannelManifest readChannelManifest(final String groupId, final String artifactId) throws IOException;

	/**
	 * Reads a Channel Manifest in a {@link org.wildfly.channel.ChannelManifest}
	 *
	 * @return {@link org.wildfly.channel.ChannelManifest}
	 * @throws IOException in case it resolves multiple Channel Manifests
	 */
	ChannelManifest readChannelManifest(final String groupId, final String artifactId, final String version) throws IOException;
}
