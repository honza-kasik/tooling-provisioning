package org.wildfly.qa.tooling.stream;

import org.wildfly.channel.Stream;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Utility class for manipulating streams collections
 */
public class StreamsManipulator {

	/**
	 * Replaces a stream's version in a stream collection
	 * @param streams stream collection
	 * @param streamGroupId stream GroupId
	 * @param streamArtifactId stream ArtifactId
	 * @param streamCurrentVersion stream Version
	 * @param streamNonExistentVersion stream new Version
	 * @return the modified stream collection
	 */
	public static Collection<Stream> replaceStreamVersion(Collection<Stream> streams, String streamGroupId, String streamArtifactId, String streamCurrentVersion, String streamNonExistentVersion) {
		Collection<Stream> manifestDeepCopy = new LinkedList<>();
		streams.stream().forEach(
				stream -> {
					if (stream.getArtifactId().equalsIgnoreCase(streamArtifactId)
							&& stream.getGroupId().equalsIgnoreCase(streamGroupId)
							&& stream.getVersion().equalsIgnoreCase(streamCurrentVersion)) {
						Stream newStream = new Stream(streamGroupId, streamArtifactId, streamNonExistentVersion);
						manifestDeepCopy.add(newStream);
					} else {
						manifestDeepCopy.add(stream);
					}
				}
		);
		return manifestDeepCopy;
	}
}
