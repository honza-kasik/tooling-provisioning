package org.wildfly.qa.tooling.maven;

import java.util.Objects;

/**
 * Maven GAV coordinates representation
 */
public class MavenCoordinates {

    private final String groupId;
    private final String artifactId;
    private final String version;

    public MavenCoordinates(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Find out if this GAV coordinate has version defined
     * @return true if version is defined, false otherwise
     */
    public boolean isVersionDefined() {
        return !(version == null || version.trim().isEmpty());
    }
}
