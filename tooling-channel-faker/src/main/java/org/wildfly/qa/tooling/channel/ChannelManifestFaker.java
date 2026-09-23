package org.wildfly.qa.tooling.channel;

import org.apache.commons.codec.digest.DigestUtils;
import org.wildfly.qa.tooling.mavenfaker.MavenArtifactFaker;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.MavenCoordinate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Tooling to create a "fake" channel manifest
 */
public class ChannelManifestFaker {

    private final MavenCoordinate coordinates;

    private final ChannelManifest channelManifest;
    private final Path channelManifestYamlFile;
    private final Path mavenRepositoryPath;

    private static final String MAVEN_METADATA_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<metadata>\n" +
            "  <groupId>${groupId}</groupId>\n" +
            "  <artifactId>${artifactId}</artifactId>\n" +
            "  <versioning>\n" +
            "    <release>${version}</release>\n" +
            "    <versions>\n" +
            "      <version>${version}</version>\n" +
            "    </versions>\n" +
            "    <lastUpdated>${time}</lastUpdated>\n" +
            "  </versioning>\n" +
            "</metadata>\n";

    /**
     * Constructor using {@link ChannelFaker.Builder}
     * @param builder {@link Builder}
     */
    private ChannelManifestFaker(Builder builder) {
        this.coordinates = builder.coordinates;
        this.channelManifest = builder.channelManifest;
        this.channelManifestYamlFile = builder.channelManifestYamlFile;
        this.mavenRepositoryPath = builder.mavenRepositoryPath;
    }

    /**
     * Installs complete fake manifest based on available data
     * @return path to installed manifest
     * @throws IOException when installation fails
     */
    public Path installFakeManifest() throws IOException {
        final MavenArtifactFaker mavenArtifactFaker = new MavenArtifactFaker.Builder()
                .artifactId(this.coordinates.getArtifactId())
                .groupId(this.coordinates.getGroupId())
                .version(this.coordinates.getVersion())
                .mavenRepoLocal(this.mavenRepositoryPath.toString())
                .build();
        final String yaml = this.channelManifestYamlFile == null ?
                ChannelManifestMapper.toYaml(this.channelManifest) :
                Files.readString(this.channelManifestYamlFile);
        mavenArtifactFaker.installRealFakeArtifactInLocalMavenRepo(yaml, "-manifest.yaml");

        installFakeMetadata(this.coordinates, mavenArtifactFaker.getGaArtifactPath());

        return mavenArtifactFaker.getGaArtifactPath();
    }

    private void installFakeMetadata(final MavenCoordinate coordinates, final Path artifactParentPath) throws IOException {
        final String correctMetadata = MAVEN_METADATA_XML.replaceAll(Pattern.quote("${version}"), coordinates.getVersion())
                .replaceAll(Pattern.quote("${groupId}"), coordinates.getGroupId())
                .replaceAll(Pattern.quote("${artifactId}"), coordinates.getArtifactId())
                .replaceAll(Pattern.quote("${time}"), String.valueOf(System.nanoTime()));

        Files.writeString(artifactParentPath.resolve("maven-metadata.xml"), correctMetadata);
        Files.writeString(artifactParentPath.resolve("maven-metadata.xml.sha1"), DigestUtils.sha1Hex(correctMetadata));
    }

    /**
     * Builder
     */
    public static final class Builder {

        private final MavenCoordinate coordinates;

        private ChannelManifest channelManifest;
        private Path channelManifestYamlFile;
        private Path mavenRepositoryPath;

        /**
         * Create new instance of this builder
         * @param coordinates coordinates of the fake channel manifest which are going to be used
         */
        public Builder(MavenCoordinate coordinates) {
            this.coordinates = coordinates;
        }

        /**
         * Create new instance of this builder
         * @param groupId groupId
         * @param artifactId artifactId
         * @param version version
         */
        public Builder(String groupId, String artifactId, String version) {
            this.coordinates = new MavenCoordinate(groupId, artifactId, version);
        }

        /**
         * Set channel manifest
         * @param channelManifest the channel manifest to install
         * @return instance of this builder
         */
        public Builder channelManifest(ChannelManifest channelManifest) {
            this.channelManifest = channelManifest;
            return this;
        }

        /**
         * Set path to a Yaml file which will be used as an alternative to channelManifest
         * @param channelManifestYamlFile path to a YAML file
         * @return instance of this builder
         */
        public Builder channelManifestYamlFile(Path channelManifestYamlFile) {
            this.channelManifestYamlFile = channelManifestYamlFile;
            return this;
        }

        /**
         * Set path of Maven repository where will be the fake channel installed
         * @param mavenRepositoryPath path of the Maven repository
         * @return instance of this builder
         */
        public Builder mavenRepositoryPath(Path mavenRepositoryPath) {
            this.mavenRepositoryPath = mavenRepositoryPath;
            return this;
        }

        /**
         * Build the {@link ChannelManifestFaker}
         * @return {@link ChannelManifestFaker}
         */
        public ChannelManifestFaker build() {
            if ((channelManifestYamlFile == null) == (channelManifest == null)) {
                throw new IllegalArgumentException("Either manifest or path to a YAML file must be set. Not both!");
            }
            return new ChannelManifestFaker(this);
        }

    }

}
