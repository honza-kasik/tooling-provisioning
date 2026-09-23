package org.wildfly.qa.tooling.featurepack.inspector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

/**
 * *-artifact-list.txt based feature pack inspector. This is artifact in respective maven path of the galleon pack.
 * e.g. /maven-repository/org/wildfly/wildfly-ee-galleon-pack/41.0.1.Final/wildfly-ee-galleon-pack-41.0.1.Final-artifact-list.txt
 */
public class ArtifactListFeaturePackInspector implements FeaturePackInspector {

    final List<ArtifactListEntry> entries;

    /**
     * Create new instance
     * @param artifactListTxtPath *-artifact-list.txt with [HASH],[PATH] format
     */
    public ArtifactListFeaturePackInspector(Path artifactListTxtPath) throws IOException {
        entries = parseArtifactListTxt(artifactListTxtPath);
    }

    @Override
    public String inspectArtifactVersion(String groupId, String artifactId) {
        return entries.stream()
                .filter(entry -> entry.matchesGroupIdArtifactId(groupId, artifactId))
                .findFirst()
                .map(ArtifactListEntry::extractVersion)
                .orElse(null);
    }

    private List<ArtifactListEntry> parseArtifactListTxt(Path file) throws IOException {
        final List<ArtifactListEntry> entries = new LinkedList<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            final String[] split = line.split(",");
            entries.add(new ArtifactListEntry(split[0], split[1]));
        }
        return entries;
    }

    private static final class ArtifactListEntry {

        final String hash;
        final String path;

        public ArtifactListEntry(final String hash, final String path) {
            this.hash = hash;
            this.path = path;
        }

        public boolean matchesGroupIdArtifactId(final String groupId, final String artifactId) {
            return this.path.startsWith(gaToPath(groupId, artifactId));
        }

        public String extractVersion() {
            final String[] split = this.path.split("/");
            return split[split.length - 2];
        }

        private String gaToPath(final String groupId, final String artifactId) {
            return "/" + groupId.replaceAll("\\.", "/") + "/" + artifactId;
        }

    }

}
