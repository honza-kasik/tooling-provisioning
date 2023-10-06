package org.jboss.eap.qe.prospero.tooling.channel;

import org.wildfly.channel.MavenCoordinate;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Tooling to create a "fake" channel
 */
public class ChannelFaker {
    private final MavenCoordinate fakeChannelMavenCoord;
    private final String fakeChannelName;

    private final String fakeChannelRepoName;
    private final URI repoUri;
    public static final String DEFAULT_FAKE_CHANNEL_NAME = "my-fake-channel";
    public static final String DEFAULT_FAKE_CHANNEL_REPO_NAME = "local-repo";
    public static final MavenCoordinate DEFAUL_FAKE_CHANNEL_MAVEN_COORD = new MavenCoordinate("org.jboss.qe.eap", "fake-channel", "1.0.0.Final");

    public ChannelFaker(Builder builder) {
        this.fakeChannelName = builder.fakeChannelName;
        this.fakeChannelRepoName = builder.fakeChannelRepoName;
        this.repoUri = builder.repoUri;
        this.fakeChannelMavenCoord = builder.fakeChannelMavenCoord;
    }

    /**
     * Get maven coordinates of fake channel
     */
    public MavenCoordinate getFakeChannelMavenCoord() {
        return fakeChannelMavenCoord;
    }

    /**
     * Get name of fake channel
     */
    public String getFakeChannelName() {
        return fakeChannelName;
    }

    /**
     * Get channel repo name
     */
    public String getFakeChannelRepoName() {
        return fakeChannelRepoName;
    }

    /**
     * Get channel repo URI
     */
    public URI getRepoUri() {
        return repoUri;
    }

    /**
     * Get yaml of fake channel
     */
    public String composeChannelYaml(boolean composeManifestVersion) throws MalformedURLException {
        //TODO maybe use channel lib?
        return "schemaVersion: \"2.0.0\"\n" +
                "name: \"" + fakeChannelName + "\"\n" +
                "repositories:\n" +
                "  - id: \"" + fakeChannelRepoName + "\"\n" +
                "    url: \"" + repoUri.toURL() + "\"\n" +
                "manifest:\n" +
                "  maven:\n" +
                "    groupId: \"" + fakeChannelMavenCoord.getGroupId() + "\"\n" +
                "    artifactId: \"" + fakeChannelMavenCoord.getArtifactId() + "\"\n" +
                (composeManifestVersion ? "    version: \"" + fakeChannelMavenCoord.getVersion() + "\"\n" : "") +
                "noStreamStrategy: \"none\"\n";
    }

    /**
     * Get prospero output of fake channel. Use different formatting for JBossCLI.
     */
    public String composeProsperoOutputChannel(boolean composeManifestVersion) throws MalformedURLException {
        return composeProsperoOutputChannel(composeManifestVersion, true);
    }

    /**
     * Get prospero output of fake channel. Use different formatting for JBossCLI.
     */
    public String composeProsperoOutputChannel(boolean composeManifestVersion, boolean platformLineBreaks) throws MalformedURLException {
        String output = "# " + fakeChannelName + "\n" +
                "  manifest: " + fakeChannelMavenCoord.getGroupId() + ":" + fakeChannelMavenCoord.getArtifactId() + (composeManifestVersion ? ":" + fakeChannelMavenCoord.getVersion() : "") + "\n" +
                "  repositories:\n" +
                "    id: " + fakeChannelRepoName + "\n" +
                "    url: " + repoUri.toURL() + "\n";
        return platformLineBreaks && isWindows() ? output.replace("\n", "\r\n") : output;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    public static final class Builder {
        private MavenCoordinate fakeChannelMavenCoord;
        private String fakeChannelName;
        private String fakeChannelRepoName;
        private URI repoUri;


        /**
         * Set repo URI in fake channel
         */
        public Builder(URI repoUri) {
            this.repoUri = repoUri;
        }

        /**
         * Set repo URI in fake channel
         */
        public Builder(Path repoPath) {
            this.repoUri = repoPath.toUri();
        }

        /**
         * Set channel manifest coordinates
         */
        public Builder fakeChannelMavenCoord(MavenCoordinate fakeChannelMavenCoord) {
            this.fakeChannelMavenCoord = fakeChannelMavenCoord;
            return this;
        }

        /**
         * Set fake channel name
         */
        public Builder fakeChannelName(String fakeChannelName) {
            this.fakeChannelName = fakeChannelName;
            return this;
        }

        /**
         * Set repo name in fake channel
         */
        public Builder fakeChannelRepoName(String fakeChannelRepoName) {
            this.fakeChannelRepoName = fakeChannelRepoName;
            return this;
        }

        /**
         * Build fake channel
         */
        public ChannelFaker build() {
            if (fakeChannelMavenCoord == null) {
                fakeChannelMavenCoord = DEFAUL_FAKE_CHANNEL_MAVEN_COORD;
            }
            if (fakeChannelName == null) {
                fakeChannelName = DEFAULT_FAKE_CHANNEL_NAME;
            }
            if (fakeChannelRepoName == null) {
                fakeChannelRepoName = DEFAULT_FAKE_CHANNEL_REPO_NAME;
            }
            return new ChannelFaker(this);
        }
    }
}
