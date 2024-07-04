package org.jboss.eap.qe.tooling.channel;

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
    /**
     * channel name
      */
    public static final String DEFAULT_FAKE_CHANNEL_NAME = "my-fake-channel";
    /**
     * maven repo name
     */
    public static final String DEFAULT_FAKE_CHANNEL_REPO_NAME = "local-repo";
    /**
     * maven coordinates
     */
    public static final MavenCoordinate DEFAUL_FAKE_CHANNEL_MAVEN_COORD = new MavenCoordinate("org.jboss.qe.eap", "fake-channel", "1.0.0.Final");

    /**
     * Constructor using {@link Builder}
     * @param builder {@link Builder}
     */
    private ChannelFaker(Builder builder) {
        this.fakeChannelName = builder.fakeChannelName;
        this.fakeChannelRepoName = builder.fakeChannelRepoName;
        this.repoUri = builder.repoUri;
        this.fakeChannelMavenCoord = builder.fakeChannelMavenCoord;
    }

    /**
     * Get maven coordinates of fake channel
     * @return {@link MavenCoordinate}
     */
    public MavenCoordinate getFakeChannelMavenCoord() {
        return fakeChannelMavenCoord;
    }

    /**
     * Get name of fake channel
     * @return name of fake channel
     */
    public String getFakeChannelName() {
        return fakeChannelName;
    }

    /**
     * Get channel repo name
     * @return repo name
     */
    public String getFakeChannelRepoName() {
        return fakeChannelRepoName;
    }

    /**
     * Get channel repo URI
     * @return repo URI
     */
    public URI getRepoUri() {
        return repoUri;
    }

    /**
     * Get yaml of fake channel
     * @param composeManifestVersion manifest version
     * @return yaml of fake channel
     */
    public String composeChannelYaml(boolean composeManifestVersion) {
        //TODO maybe use channel lib?
        return "schemaVersion: \"2.0.0\"\n" +
                "name: \"" + fakeChannelName + "\"\n" +
                "repositories:\n" +
                "  - id: \"" + fakeChannelRepoName + "\"\n" +
                "    url: \"" + repoUri + "\"\n" +
                "manifest:\n" +
                "  maven:\n" +
                "    groupId: \"" + fakeChannelMavenCoord.getGroupId() + "\"\n" +
                "    artifactId: \"" + fakeChannelMavenCoord.getArtifactId() + "\"\n" +
                (composeManifestVersion ? "    version: \"" + fakeChannelMavenCoord.getVersion() + "\"\n" : "") +
                "resolve-if-no-stream: \"none\"\n";
    }

    /**
     * Get prospero output of fake channel. Use different formatting for JBossCLI.
     * @param composeManifestVersion amnifest version
     * @return prospero output
     * @throws MalformedURLException when repo URI is malformed
     */
    public String composeProsperoOutputChannel(boolean composeManifestVersion) throws MalformedURLException {
        return composeProsperoOutputChannel(composeManifestVersion, true);
    }

    /**
     * Get prospero output of fake channel. Use different formatting for JBossCLI.
     * @param composeManifestVersion manifest version
     * @param platformLineBreaks add line break
     * @return prospero output
     * @throws MalformedURLException when repo URI is malformed
     */
    public String composeProsperoOutputChannel(boolean composeManifestVersion, boolean platformLineBreaks) throws MalformedURLException {
        String output = "# " + fakeChannelName + "\n" +
                "  manifest: " + fakeChannelMavenCoord.getGroupId() + ":" + fakeChannelMavenCoord.getArtifactId() + (composeManifestVersion ? ":" + fakeChannelMavenCoord.getVersion() : "") + "\n" +
                "  repositories:\n" +
                "    id: " + fakeChannelRepoName + "\n" +
                "    url: " + repoUri.toURL() + "\n";
        return platformLineBreaks && isWindows() ? output.replace("\n", "\r\n") : output;
    }

    /**
     * Are we on Windows?
     * @return true: we are on Windows
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Builder
     */
    public static final class Builder {
        private MavenCoordinate fakeChannelMavenCoord;
        private String fakeChannelName;
        private String fakeChannelRepoName;
        private final URI repoUri;


        /**
         * Set repo URI in fake channel
         * @param repoUri repo URI
         */
        public Builder(URI repoUri) {
            this.repoUri = repoUri;
        }

        /**
         * Set repo path in fake channel
         * @param repoPath repo path
         */
        public Builder(Path repoPath) {
            this.repoUri = repoPath.toUri();
        }

        /**
         * Set channel manifest coordinates
         * @param fakeChannelMavenCoord channel manifest coordinates
         * @return {@link Builder}
         */
        public Builder fakeChannelMavenCoord(MavenCoordinate fakeChannelMavenCoord) {
            this.fakeChannelMavenCoord = fakeChannelMavenCoord;
            return this;
        }

        /**
         * Set fake channel name
         * @param fakeChannelName channel name
         * @return {@link Builder}
         */
        public Builder fakeChannelName(String fakeChannelName) {
            this.fakeChannelName = fakeChannelName;
            return this;
        }

        /**
         * Set repo name in fake channel
         * @param fakeChannelRepoName repo name
         * @return {@link Builder}
         */
        public Builder fakeChannelRepoName(String fakeChannelRepoName) {
            this.fakeChannelRepoName = fakeChannelRepoName;
            return this;
        }

        /**
         * Build fake channel
         * @return {@link ChannelFaker}
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
