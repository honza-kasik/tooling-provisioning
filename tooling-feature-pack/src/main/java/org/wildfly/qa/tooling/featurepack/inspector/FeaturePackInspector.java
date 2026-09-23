package org.wildfly.qa.tooling.featurepack.inspector;

/**
 * An interface to inspect feature packs for artifact versions.
 */
public interface FeaturePackInspector {

    /**
     * Inspects the version of a particular artifact in the feature pack.
     *
     * @param groupId the group ID of the artifact
     * @param artifactId the artifact ID of the artifact
     * @return the artifact version or {@code null} if none was found
     */
    String inspectArtifactVersion(String groupId, String artifactId);
}
