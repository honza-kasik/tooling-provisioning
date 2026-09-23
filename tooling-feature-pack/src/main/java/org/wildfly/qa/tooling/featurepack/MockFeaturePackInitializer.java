package org.wildfly.qa.tooling.featurepack;

import org.wildfly.qa.tooling.channel.ChannelManifestFaker;
import org.wildfly.qa.tooling.mavenfaker.MavenArtifactFaker;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeatureConfig;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.creator.FeaturePackBuilder;
import org.jboss.galleon.creator.FeaturePackCreator;
import org.jboss.galleon.spec.ConfigLayerSpec;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.SimplisticMavenRepoManager;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.channel.Stream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Initialize (build and install) a mock feature pack. Be aware that some side effects happen here including installing
 * artifact in a maven repo.
 */
public class MockFeaturePackInitializer {

    private final Stream productGalleonPackStream;
    private final MavenCoordinate featurePackCoordinate;
    private final MavenCoordinate channelManifestCoordinate;
    private final Path featurePackChannelRepo;
    private final String packageTwoContent;
    private final boolean includeDefaultConfig;

    public MockFeaturePackInitializer(Builder builder) {
        this.productGalleonPackStream = builder.productGalleonPackStream;
        this.featurePackCoordinate = builder.featurePackCoordinate;
        this.channelManifestCoordinate = builder.channelManifestCoordinate;
        this.featurePackChannelRepo = builder.featurePackChannelRepo;
        this.packageTwoContent = builder.packageTwoContent;
        this.includeDefaultConfig = builder.includeDefaultConfig;
    }

    public static final class Builder {

        private final Stream productGalleonPackStream;
        private MavenCoordinate featurePackCoordinate;
        private MavenCoordinate channelManifestCoordinate;
        private Path featurePackChannelRepo;
        private String packageTwoContent;
        private boolean includeDefaultConfig = false;

        /**
         * Create new instance of the builder
         * @param productGalleonPackStream stream of feature pack which will be used as dependency for the newly
         *                                 initialized feature pack. It must contain definition of JSF subsystem.
         */
        public Builder(Stream productGalleonPackStream) {
            this.productGalleonPackStream = productGalleonPackStream;
        }

        /**
         * Set coordinate of newly created feature pack
         * @return instance of this builder
         */
        public Builder featurePackCoordinate(String groupId, String artifactId, String version) {
            this.featurePackCoordinate = new MavenCoordinate(groupId, artifactId, version);
            return this;
        }

        /**
         * Set coordinate of newly created feature pack
         * @return instance of this builder
         */
        public Builder featurePackCoordinate(MavenCoordinate featurePackCoordinate) {
            this.featurePackCoordinate = featurePackCoordinate;
            return this;
        }

        /**
         * Set coordinate of manifest which will include the newly created feature pack
         * @return instance of this builder
         */
        public Builder channelManifestCoordinate(String groupId, String artifactId, String version) {
            this.channelManifestCoordinate = new MavenCoordinate(groupId, artifactId, version);
            return this;
        }

        /**
         * Set coordinate of manifest which will include the newly created feature pack
         * @return instance of this builder
         */
        public Builder channelManifestCoordinate(MavenCoordinate channelManifestCoordinate) {
            this.channelManifestCoordinate = channelManifestCoordinate;
            return this;
        }

        /**
         * Set repository backing the channel. This repository will contain both feature pack and feature pack manifest
         * when fully initialized.
         * @param path Existing path to a directory
         * @return instance of this builder
         */
        public Builder featurePackChannelRepo(final Path path) {
            this.featurePackChannelRepo = path;
            return this;
        }

        /**
         * Set content of a qix/qux.txt in package #2. This can be later used for verifications.
         * @param packageTwoContent a string to be written in the file
         * @return instance of this builder
         */
        public Builder packageTwoContent(final String packageTwoContent) {
            this.packageTwoContent = packageTwoContent;
            return this;
        }

        /**
         * Should the feature pack contain default configuration?
         * @param includeDefaultConfig if true, feature pack wil contain default config
         * @return instance of this builder
         */
        public Builder includeDefaultConfig(boolean includeDefaultConfig) {
            this.includeDefaultConfig = includeDefaultConfig;
            return this;
        }

        public MockFeaturePackInitializer build() {
            return new MockFeaturePackInitializer(this);
        }

    }

    /**
     * Let's define a feature pack this one will have:
     * - packages
     *   - package1
     *     - foo/bar.txt with 'qix' content
     *   - package2
     *     - qix/qux.txt with content equal to 'package2Content' param
     * - layers
     *   - layer1
     *     - specifying package1 as dependency
     *   - layer2
     *     - specifying package2 as dependency
     *   - layer3
     *     - specifying package1 as dependency + including configuration for JSF subsystem
     * - config
     *   - standalone (no other model currently makes sense for WildFly's FPs, this might change in the future)
     *     - config1
     *       - configures layer2 to be installed
     *     - standalone.xml (specified if hasDefaultConfig == true)
     *       - default per Galleon spec. Described config should be installed when no layer is specified.
     *
     * This feature pack will be installed to {@code featurePackChannelRepo} and it is dependent on the default WildFly
     * feature pack to be able to edit WildFly's config since this FP defines all necessary specs.
     */
    public void initTestFeaturePack() throws ProvisioningException, IOException {

        final String featurePackCoordString = String.join(":", List.of(
                this.featurePackCoordinate.getGroupId(),
                this.featurePackCoordinate.getArtifactId(),
                this.featurePackCoordinate.getVersion()));

        FeaturePackCreator featurePackCreator = new FeaturePackCreator();
        //this will set target for install to a repository
        featurePackCreator.addArtifactResolver(SimplisticMavenRepoManager.getInstance(this.featurePackChannelRepo));

        FeaturePackBuilder featurePackBuilder = featurePackCreator.newFeaturePack(FeaturePackLocation.fromString(featurePackCoordString).getFPID())
                .newPackage("package1")
                .writeContent("foo/bar.txt", "qix")
                .getFeaturePack()
                .newPackage("package2")
                .writeContent("qix/qux.txt", this.packageTwoContent)
                .getFeaturePack()
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("standalone")
                        .setName("layer1")
                        .addPackageDep("package1", false)
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("standalone")
                        .setName("layer2")
                        .addPackageDep("package2", false)
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("standalone")
                        .setName("layer3")
                        .addPackageDep("package1", false)
                        .addFeature(FeatureConfig.newConfig("subsystem.jsf")
                                .setParam("default-jsf-impl-slot", "foobar"))
                        .addLayerDep("jsf")
                        .build())
                //dependency is needed so Galleon will understand spec of feature above (subsystem.jsf)
                .addDependency(FeaturePackConfig.forLocation(
                        FeaturePackLocation.fromString(String.join(":",
                                this.productGalleonPackStream.getGroupId(),
                                this.productGalleonPackStream.getArtifactId(),
                                this.productGalleonPackStream.getVersion()))));

        if (this.includeDefaultConfig) {
            //default config https://docs.wildfly.org/galleon-plugins/#_galleon_default_configurations
            featurePackBuilder.addConfig(ConfigModel.builder()
                    .setModel("standalone")
                    .setName("standalone.xml")
                    .addPackageDep("package1", false)
                    .includeLayer("layer1").build(), true);
        }

        //not the best API, but this will cause an FP being built to a ZIP and its install to a repository set before
        featurePackCreator.install();

        //make a checksum for the feature pack ZIP
        MavenArtifactFaker.writeSha1Sum(composePathInRepo(this.featurePackChannelRepo, this.featurePackCoordinate.getGroupId(),
                        this.featurePackCoordinate.getArtifactId(), this.featurePackCoordinate.getVersion())
                .resolve(this.featurePackCoordinate.getArtifactId() + "-" + this.featurePackCoordinate.getVersion() + ".zip"));

        //create fake manifest with the feature pack and install it in the local repo
        new ChannelManifestFaker.Builder(channelManifestCoordinate.getGroupId(), channelManifestCoordinate.getArtifactId(),
                channelManifestCoordinate.getVersion())
                .channelManifest(new ChannelManifest("Test feature pack channel manifest", channelManifestCoordinate.getArtifactId(),
                        "Just a feature pack manifest for testing purposes",
                        Collections.singleton(new Stream(this.featurePackCoordinate.getGroupId(),
                                this.featurePackCoordinate.getArtifactId(), this.featurePackCoordinate.getVersion()))))
                .mavenRepositoryPath(this.featurePackChannelRepo)
                .build()
                .installFakeManifest();
    }

    private static Path composePathInRepo(Path mavenRepo, String groupId, String artifactId, String version) {
        return mavenRepo.resolve(groupId.replace(".", File.separator))
                .resolve(artifactId)
                .resolve(version);
    }

}
