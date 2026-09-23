package org.wildfly.qa.tooling.channel;

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for reading a channel manifest from a local maven repo
 */
public class ChannelManifestLocalRepoReader implements ChannelManifestReader {

	final String channelManifestVersion;
	final Path mavenRepositoryPath;

	/**
	 * Create an instance of ChannelManifestLocalRepoReader
	 * @param channelManifestVersion version
	 * @param mavenRepositoryPath Path to maven repo
	 */
	public ChannelManifestLocalRepoReader(
			String channelManifestVersion,
			Path mavenRepositoryPath) {
		this.channelManifestVersion = channelManifestVersion;
		this.mavenRepositoryPath = mavenRepositoryPath;
	}

	/**
	 * Create an instance of ChannelManifestLocalRepoReader
	 * @param mavenRepositoryPath Path to maven repo
	 */
	public ChannelManifestLocalRepoReader(
			Path mavenRepositoryPath) {
		this(null, mavenRepositoryPath);
	}

	/**
	 * Reads a Channel Manifest in a {@link org.wildfly.channel.ChannelManifest} from a local maven repository
	 *
	 * @return {@link org.wildfly.channel.ChannelManifest}
	 * @throws IOException in case it resolves multiple Channel Manifests
	 */
	public ChannelManifest readChannelManifest(final String channelManifestGroupId, final String channelManifestArtifactId) throws IOException {
		return readChannelManifest(channelManifestGroupId, channelManifestArtifactId, null);
	}

	/**
	 * Reads a Channel Manifest in a {@link org.wildfly.channel.ChannelManifest} from a local maven repository
	 *
	 * @return {@link org.wildfly.channel.ChannelManifest}
	 * @throws IOException in case it resolves multiple Channel Manifests
	 */
	public ChannelManifest readChannelManifest(final String channelManifestGroupId,
											   final String channelManifestArtifactId,
											   final String channelManifestVersion) throws IOException {
		final Path gaPath = channelManifestVersion == null ?
				mavenRepositoryPath.resolve(groupIdToPath(channelManifestGroupId)).resolve(channelManifestArtifactId)
				:
				mavenRepositoryPath
						.resolve(groupIdToPath(channelManifestGroupId))
						.resolve(channelManifestArtifactId)
						.resolve(channelManifestVersion);
		try (java.util.stream.Stream<Path> visitedPaths = Files.walk(gaPath)) {
			final List<Path> yamlPaths = visitedPaths
					.filter(path -> path.toString().endsWith("yaml"))
					.collect(Collectors.toList());
			if (yamlPaths.size() > 1) {
				throw new IllegalStateException("There are multiple YAML artifacts of same group ID and artifact ID. This was " +
						"not accounted for. Aborting... All YAML artifacts found: \n  " + yamlPaths.stream().map(Path::toString).collect(Collectors.joining("  \n")));
			} else {
				return ChannelManifestMapper.fromString(Files.readString(yamlPaths.get(0)));
			}
		}
	}


	private Path groupIdToPath(final String groupId) {
		return Paths.get(groupId.replace(".", "/"));
	}
}
