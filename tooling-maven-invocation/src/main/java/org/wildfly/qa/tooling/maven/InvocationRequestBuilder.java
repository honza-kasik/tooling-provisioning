package org.wildfly.qa.tooling.maven;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.wildfly.qa.tooling.resources.ResourcePathLocator;
import org.wildfly.qa.tooling.strings.FileStringReplacer;
import org.wildfly.qa.tooling.strings.ReplacementException;
import org.wildfly.qa.tooling.strings.ReplacementPairs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Builder for {@link InvocationRequest}, reducing code redundancy
 */
public class InvocationRequestBuilder {

    private static final Logger log = Logger.getLogger(InvocationRequestBuilder.class.getName());

    private File pomFilePath;
    private File userSettingsFile;
    private List<String> mavenGoals;

    // wildfly jar properties
    private String wildflyJarPluginVersion;
    private boolean wildflyJarPluginDevMode = false;
    private boolean dumpOriginalArtifacts = false;
    private List<String> profiles;

    private String mavenRepoLocalPath;
    private Properties otherProperties;
    @Deprecated
    private boolean useBrewRepo;

    private MavenCoordinates mavenPluginGav;
    private MavenCoordinates galleonPackGav;
    private MavenCoordinates eeGalleonPackGav;
    private MavenCoordinates cloudGalleonPackGav;
    private List<MavenCoordinates> channelManifestGavs = new LinkedList<>();
    private Boolean mavenDebug = false;

    public InvocationRequestBuilder() {
        //no default behavior required
    }

    public InvocationRequestBuilder setMavenDebug(Boolean mavenDebug) {
        this.mavenDebug = mavenDebug;
        return this;
    }

    /**
     * Set maven goals
     * @param mavenGoals set of Maven goals to invoke (e.g. "clean", "test")
     * @return instance of this builder
     */
    public InvocationRequestBuilder setGoals(final String... mavenGoals) {
        this.mavenGoals = Arrays.asList(mavenGoals);
        return this;
    }

    /**
     * Set path to invoked pom.xml relative to test resources folder
     * @param pomFilePath path to pom.xml (e.g. "pom-configs/pom-feature-pack-missing.xml")
     * @return instance of this builder
     */
    public InvocationRequestBuilder setPomFilePath(final String pomFilePath) {
        this.pomFilePath = ResourcePathLocator.getResourceAbsolutePath(pomFilePath).toFile();
        return this;
    }

    /**
     * Set WildFly Maven JAR plugin version
     * @param wildflyJarMavenPluginVersion WildFly JAR Maven plugin
     * @return instance of this builder
     */
    public InvocationRequestBuilder setWildflyJarMavenPluginVersion(final String wildflyJarMavenPluginVersion) {
        this.wildflyJarPluginVersion = wildflyJarMavenPluginVersion;
        return this;
    }

    /**
     * Set WildFly Maven plugin GAV
     * @param mavenPluginGav Maven plugin GAV
     * @return instance of this builder
     */
    public InvocationRequestBuilder setMavenPluginGav(final MavenCoordinates mavenPluginGav) {
        this.mavenPluginGav = mavenPluginGav;
        return this;
    }

    /**
     * Set WildFly Maven JAR plugin to run in the dev mode, i.e. dev property is added: -Ddev
     * @return instance of this builder
     */
    public InvocationRequestBuilder setWildflyJarMavenPluginDevMode() {
        this.wildflyJarPluginDevMode = true;
        return this;
    }

    /**
     * Set ee-galleon-pack GAV coordinates
     * @param eeGalleonPackGav Galleon pack GAV coordinates
     * @return instance of this builder
     */
    public InvocationRequestBuilder setEeGalleonPackGav(MavenCoordinates eeGalleonPackGav) {
        this.eeGalleonPackGav = eeGalleonPackGav;
        return this;
    }

    /**
     * Set galleon-pack GAV coordinates
     * @param galleonPackGav Galleon pack GAV coordinates
     * @return instance of this builder
     */
    public InvocationRequestBuilder setGalleonPackGav(MavenCoordinates galleonPackGav) {
        this.galleonPackGav = galleonPackGav;
        return this;
    }

    /**
     * Set cloud-galleon-pack GAV coordinates
     * @param cloudGalleonPackGav Galleon pack GAV coordinates
     * @return instance of this builder
     */
    public InvocationRequestBuilder setCloudGalleonPackGav(MavenCoordinates cloudGalleonPackGav) {
        this.cloudGalleonPackGav = cloudGalleonPackGav;
        return this;
    }

    /**
     * Add channel manifest GAV coordinates to list of GAVs. First manifest will replace placeholders.
     * This method is also used in setDefaultProperties() method, so that default manifest could be first manifest.
     * {@code testsuite.channel.manifest.*} in pom.xml , every following <i>i</i> one will replace
     * {@code testsuite.channel.manifest[i].*}
     * @param channelManifestGav Channel manifest GAV coordinates
     * @return instance of this builder
     */
    public InvocationRequestBuilder addChannelManifestGav(MavenCoordinates channelManifestGav) {
        this.channelManifestGavs.add(channelManifestGav);
        return this;
    }

    /**
     * Set list of channel manifest GAV coordinates. First manifest will replace placeholders
     * {@code testsuite.channel.manifest.*} in pom.xml , every following <i>i</i> one will replace
     * {@code testsuite.channel.manifest[i].*}
     * @param channelManifestGavs List of channel manifest GAV coordinates
     * @return instance of this builder
     */
    public InvocationRequestBuilder setChannelManifestGavs(List<MavenCoordinates> channelManifestGavs) {
        this.channelManifestGavs = channelManifestGavs;
        return this;
    }

    /**
     * Clean list of channel manifest gavs. Create new list wtih manifest GAV coordinates from method parameter to list of GAVs.
     * {@code testsuite.channel.manifest.*} in pom.xml , every following <i>i</i> one will replace
     * {@code testsuite.channel.manifest[i].*}
     * @param channelManifestGav Channel manifest GAV coordinates
     * @return instance of this builder
     */
    public InvocationRequestBuilder setChannelManifestGav(MavenCoordinates channelManifestGav) {
        this.channelManifestGavs = new LinkedList<>();
        this.channelManifestGavs.add(channelManifestGav);
        return this;
    }

    /**
     * Enable dump of upgradable artifacts to a file
     * @return instance of this builder
     */
    public InvocationRequestBuilder setDumpOriginalArtifacts() {
        this.dumpOriginalArtifacts = true;
        return this;
    }

    /**
     * Use for properties like artifact versions which are not controlling the invocation and are used one-time mostly.
     * @param key property key
     * @param value property value
     * @return instance of this builder
     */
    public InvocationRequestBuilder setProperty(final String key, final String value) {
        if (this.otherProperties == null) {
            this.otherProperties = new Properties();
        }
        this.otherProperties.setProperty(key, value);
        return this;
    }

    /**
     * Set path to local Maven repository. This path will be used by invoked Maven instance.
     * @param mavenRepoLocalPath absolute path to local maven repository
     * @see <a href="https://maven.apache.org/guides/mini/guide-configuring-maven.html">
     *     https://maven.apache.org/guides/mini/guide-configuring-maven.html</a>
     * @return instance of this builder
     */
    public InvocationRequestBuilder setMavenRepoLocalPath(final String mavenRepoLocalPath) {
        this.mavenRepoLocalPath = mavenRepoLocalPath;
        return this;
    }

    /**
     * Set whether the Maven profile that declares the internal Brew repo should be activated.
     * This flag will be used by invoked Maven instance.
     * @param useBrewRepo sets the property that activates the {@code use-brew-repo} profile.
     * @return instance of this builder
     * @deprecated Use {@link #setUserSettingsFile(String)} with proper settings instead to configure repository
     */
    @Deprecated
    public InvocationRequestBuilder setUseBrewRepo(final boolean useBrewRepo) {
        this.useBrewRepo = useBrewRepo;
        return this;
    }

    /**
     * Set path to user settings.xml relative to resources directory.
     * @param userSettingsFile path to settings.xml
     * @return instance of this builder
     */
    public InvocationRequestBuilder setUserSettingsFile(final String userSettingsFile) {
        this.userSettingsFile = ResourcePathLocator.getResourceAbsolutePath(userSettingsFile).toFile();
        return this;
    }

    /**
     * Set path to user settings.xml relative to resources directory and edit provided file.
     * @param userSettingsFile path to settings.xml
     * @param pairs pairs of pattern-replacement to be used in editing provided settings xml. Each found pattern in file
     *              will be replaced by corresponding replacement
     * @return instance of this builder
     */
    public InvocationRequestBuilder setUserSettingsFileWithReplacements(final String userSettingsFile,
                                                                        final ReplacementPairs pairs) {
        final Path filePath = ResourcePathLocator.getResourceAbsolutePath(userSettingsFile);

        try {
            new FileStringReplacer(filePath, pairs).replaceAll();
        } catch (ReplacementException e) {
            throw new RuntimeException(e);
        }

        return setUserSettingsFile(userSettingsFile);
    }

    public InvocationRequest build() {
        validate();

        final InvocationRequest request = new DefaultInvocationRequest();
        request.setBatchMode(true);
        request.setPomFile(this.pomFilePath);
        request.setGoals(this.mavenGoals);
        request.setProperties(buildProperties());
        if (channelManifestGavs.get(0).getArtifactId() != null && !channelManifestGavs.get(0).getArtifactId().isEmpty()
                && channelManifestGavs.get(0).getGroupId() != null && !channelManifestGavs.get(0).getGroupId().isEmpty()
                && channelManifestGavs.get(0).getVersion() != null && !channelManifestGavs.get(0).getVersion().isEmpty()) {
            request.setProfiles(Collections.singletonList("bootable.jar.channel"));
        }
        request.setLocalRepositoryDirectory(new File(this.mavenRepoLocalPath));
        request.setDebug(this.mavenDebug);

        if (this.userSettingsFile != null) {
            request.setUserSettingsFile(this.userSettingsFile);
        }

        return request;
    }

    private void validate() {
        if (eeGalleonPackGav != null && eeGalleonPackGav.isVersionDefined()) {
            log.warning("ee-galleon-pack has version defined. If channel manifest provides this galleon pack, defined " +
                    "version will not be used!");
        }
        if (cloudGalleonPackGav != null && cloudGalleonPackGav.isVersionDefined()) {
            log.warning("cloud-galleon-pack has version defined. If channel manifest provides this galleon pack, " +
                    "defined version will not be used!");
        }
        Objects.requireNonNull(this.mavenRepoLocalPath, "Local Maven cache path must be set!");
        final Path localMavenRepoPath = Paths.get(this.mavenRepoLocalPath);
        if (!Files.exists(localMavenRepoPath) || !Files.isDirectory(localMavenRepoPath)) {
            throw new IllegalArgumentException("Local maven repo path must exist and must be a directory!");
        }
    }

    private Properties buildProperties() {
        final Properties properties = new Properties();
        setPropertyIfNotNull(properties, "version.org.wildfly.jar.plugin", this.wildflyJarPluginVersion);
        if (this.mavenPluginGav != null) {
            setPropertyIfNotNull(properties, "testsuite.wildfly-maven-plugin.groupId", this.mavenPluginGav.getGroupId());
            setPropertyIfNotNull(properties, "testsuite.wildfly-maven-plugin.artifactId", this.mavenPluginGav.getArtifactId());
            setPropertyIfNotNull(properties, "testsuite.wildfly-maven-plugin.version", this.mavenPluginGav.getVersion());
        }
        if (this.galleonPackGav != null) {
            setPropertyIfNotNull(properties, "testsuite.galleon.pack.groupId", this.galleonPackGav.getGroupId());
            setPropertyIfNotNull(properties, "testsuite.galleon.pack.artifactId", this.galleonPackGav.getArtifactId());
            setPropertyIfNotNull(properties, "testsuite.galleon.pack.version", this.galleonPackGav.getVersion());
        }
        if (this.eeGalleonPackGav != null) {
            setPropertyIfNotNull(properties, "testsuite.ee-galleon-pack.groupId", this.eeGalleonPackGav.getGroupId());
            setPropertyIfNotNull(properties, "testsuite.ee-galleon-pack.artifactId", this.eeGalleonPackGav.getArtifactId());
            setPropertyIfNotNull(properties, "testsuite.ee-galleon-pack.version", this.eeGalleonPackGav.getVersion());
        }
        if (this.cloudGalleonPackGav != null) {
            setPropertyIfNotNull(properties, "testsuite.cloud-galleon-pack.groupId", this.cloudGalleonPackGav.getGroupId());
            setPropertyIfNotNull(properties, "testsuite.cloud-galleon-pack.artifactId", this.cloudGalleonPackGav.getArtifactId());
            setPropertyIfNotNull(properties, "testsuite.cloud-galleon-pack.version", this.cloudGalleonPackGav.getVersion());
        }
        for (int i = 0; i < this.channelManifestGavs.size(); i ++) {
            final MavenCoordinates gav = this.channelManifestGavs.get(i);
            final String manifestId = i == 0 ? "" : String.valueOf(i);
            setPropertyIfNotNull(properties, "testsuite.channel.manifest" + manifestId + ".groupId", gav.getGroupId());
            setPropertyIfNotNull(properties, "testsuite.channel.manifest" + manifestId + ".artifactId", gav.getArtifactId());
            setPropertyIfNotNull(properties, "testsuite.channel.manifest" + manifestId + ".version", gav.getVersion());
        }
        if (this.wildflyJarPluginDevMode) {
            properties.setProperty("dev", "");
        }
        if (this.dumpOriginalArtifacts) {
            properties.setProperty("bootable.jar.dump.original.artifacts", Boolean.toString(true));
        }
        if (this.useBrewRepo) {
            // explicitly set to true, but just the name would work as well...
            properties.setProperty("use.brew.repo", Boolean.toString(true));
        }
        if (this.otherProperties != null) {
            properties.putAll(this.otherProperties);
        }
        return properties;
    }

    private void setPropertyIfNotNull(Properties properties, String propertyName, String propertyValue) {
        if (propertyValue != null) {
            properties.setProperty(propertyName, propertyValue);
        }
    }

}
