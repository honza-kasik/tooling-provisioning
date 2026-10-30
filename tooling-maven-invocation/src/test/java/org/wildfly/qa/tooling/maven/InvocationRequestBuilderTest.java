package org.wildfly.qa.tooling.maven;

import org.apache.maven.shared.invoker.InvocationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.wildfly.qa.tooling.strings.ReplacementPairs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link InvocationRequestBuilder}.
 * Verifies that all builder methods correctly propagate their values
 * to the built {@link InvocationRequest}.
 */
class InvocationRequestBuilderTest {

    @TempDir
    Path tempDir;

    // --- Settings file ---

    /**
     * setUserSettingsFileAbsolutePath sets the settings file on the built request
     * using the provided absolute path directly, without classpath resolution.
     * Passes when getUserSettingsFile() returns a File matching the absolute path.
     */
    @Test
    void absolutePathSettingsFileIsUsedDirectly() throws IOException {
        Path settingsFile = tempDir.resolve("custom-settings.xml");
        Files.writeString(settingsFile, "<settings/>");

        InvocationRequest request = minimalBuilder()
                .setUserSettingsFileAbsolutePath(settingsFile.toAbsolutePath().toString())
                .build();

        assertEquals(settingsFile.toAbsolutePath().toFile(), request.getUserSettingsFile());
    }

    /**
     * When no settings file method is called, the built request has no user settings.
     * Passes when getUserSettingsFile() returns null.
     */
    @Test
    void noSettingsFileWhenNotSet() {
        InvocationRequest request = minimalBuilder().build();

        assertNull(request.getUserSettingsFile());
    }

    /**
     * setUserSettingsFile with a path not on the classpath throws IllegalStateException
     * from ResourcePathLocator. This confirms that setUserSettingsFile resolves from the
     * classpath and cannot be used for arbitrary filesystem paths.
     * Passes when IllegalStateException is thrown.
     */
    @Test
    void classpathSettingsFileThrowsForNonResourcePath() {
        assertThrows(IllegalStateException.class, () ->
                minimalBuilder().setUserSettingsFile("/nonexistent/path/settings.xml"));
    }

    /**
     * setUserSettingsFile resolves a classpath resource and sets it on the built request.
     * The resolved file must exist on disk (under the test resources directory).
     * Passes when getUserSettingsFile() returns an existing file.
     */
    @Test
    void classpathSettingsFileResolvesFromResources() {
        InvocationRequest request = minimalBuilder()
                .setUserSettingsFile("maven-settings/test-settings.xml")
                .build();

        File settingsFile = request.getUserSettingsFile();
        assertTrue(settingsFile.exists(), "Resolved settings file should exist on disk");
        assertTrue(settingsFile.getAbsolutePath().endsWith("maven-settings/test-settings.xml"));
    }

    /**
     * setUserSettingsFileWithReplacements resolves a classpath resource, performs placeholder
     * replacements in the file content, and sets the result on the built request.
     * Passes when the placeholder in the file is replaced with the provided value.
     * The original file is restored after the test to avoid polluting shared test resources.
     */
    @Test
    void settingsFileWithReplacementsPerformsSubstitution() throws IOException {
        Path originalFile = Path.of(Thread.currentThread().getContextClassLoader()
                .getResource("maven-settings/test-settings.xml").getPath());
        String originalContent = Files.readString(originalFile);
        try {
            InvocationRequest request = minimalBuilder()
                    .setUserSettingsFileWithReplacements("maven-settings/test-settings.xml",
                            new ReplacementPairs()
                                    .addPair("${test-repo-url}", "file:///tmp/test-repo"))
                    .build();

            File settingsFile = request.getUserSettingsFile();
            String content = Files.readString(settingsFile.toPath());
            assertTrue(content.contains("file:///tmp/test-repo"),
                    "Placeholder should be replaced with the provided URL");
            assertFalse(content.contains("${test-repo-url}"),
                    "Original placeholder should no longer be present");
        } finally {
            Files.writeString(originalFile, originalContent);
        }
    }

    // --- Goals ---

    /**
     * setGoals propagates the specified goals to the built request.
     * Passes when getGoals() returns the list of goals in order.
     */
    @Test
    void goalsAreSetOnRequest() {
        InvocationRequest request = minimalBuilder()
                .setGoals("clean", "install")
                .build();

        assertEquals(Arrays.asList("clean", "install"), request.getGoals());
    }

    // --- Local repository ---

    /**
     * setMavenRepoLocalPath sets the local repository directory on the built request.
     * Passes when getLocalRepositoryDirectory(null) matches the provided path.
     */
    @Test
    void localRepositoryPathIsSetOnRequest() {
        InvocationRequest request = minimalBuilder().build();

        assertEquals(tempDir.toAbsolutePath().toFile(), request.getLocalRepositoryDirectory(null));
    }

    /**
     * build() throws NullPointerException when mavenRepoLocalPath is not set.
     * Passes when NullPointerException is thrown.
     */
    @Test
    void buildFailsWithoutLocalRepoPath() {
        assertThrows(NullPointerException.class, () ->
                new InvocationRequestBuilder()
                        .setGoals("validate")
                        .addChannelManifestGav(new MavenCoordinates("", "", ""))
                        .build());
    }

    /**
     * build() throws IllegalArgumentException when mavenRepoLocalPath points to a
     * non-existent directory.
     * Passes when IllegalArgumentException is thrown.
     */
    @Test
    void buildFailsWithNonExistentLocalRepoPath() {
        assertThrows(IllegalArgumentException.class, () ->
                new InvocationRequestBuilder()
                        .setGoals("validate")
                        .setMavenRepoLocalPath("/nonexistent/path")
                        .addChannelManifestGav(new MavenCoordinates("", "", ""))
                        .build());
    }

    // --- Batch mode ---

    /**
     * The built request always has batch mode enabled.
     * Passes when isBatchMode() returns true.
     */
    @Test
    void batchModeIsAlwaysEnabled() {
        InvocationRequest request = minimalBuilder().build();

        assertTrue(request.isBatchMode());
    }

    // --- Debug ---

    /**
     * setMavenDebug(true) enables debug output on the built request.
     * Passes when isDebug() returns true.
     */
    @Test
    void debugModeCanBeEnabled() {
        InvocationRequest request = minimalBuilder()
                .setMavenDebug(true)
                .build();

        assertTrue(request.isDebug());
    }

    /**
     * Debug mode is disabled by default.
     * Passes when isDebug() returns false.
     */
    @Test
    void debugModeIsDisabledByDefault() {
        InvocationRequest request = minimalBuilder().build();

        assertFalse(request.isDebug());
    }

    // --- Channel manifest GAV properties ---

    /**
     * A single channel manifest GAV with non-empty coordinates activates the
     * bootable.jar.channel profile and sets the manifest properties without a
     * numeric suffix.
     * Passes when the profile is present and properties match.
     */
    @Test
    void singleChannelManifestActivatesProfileAndSetsProperties() {
        InvocationRequest request = new InvocationRequestBuilder()
                .setGoals("validate")
                .setMavenRepoLocalPath(tempDir.toAbsolutePath().toString())
                .addChannelManifestGav(new MavenCoordinates("org.test", "manifest", "1.0"))
                .build();

        assertTrue(request.getProfiles().contains("bootable.jar.channel"));
        Properties props = request.getProperties();
        assertEquals("org.test", props.getProperty("testsuite.channel.manifest.groupId"));
        assertEquals("manifest", props.getProperty("testsuite.channel.manifest.artifactId"));
        assertEquals("1.0", props.getProperty("testsuite.channel.manifest.version"));
    }

    /**
     * An empty channel manifest GAV (all fields empty) does not activate the
     * bootable.jar.channel profile.
     * Passes when getProfiles() does not contain the channel profile.
     */
    @Test
    void emptyChannelManifestDoesNotActivateProfile() {
        InvocationRequest request = minimalBuilder().build();

        List<String> profiles = request.getProfiles();
        assertTrue(profiles == null || !profiles.contains("bootable.jar.channel"));
    }

    /**
     * Multiple channel manifest GAVs set indexed properties for the second and
     * subsequent manifests (manifest1, manifest2, etc.).
     * Passes when both the unindexed and indexed properties are set correctly.
     */
    @Test
    void multipleChannelManifestsSetIndexedProperties() {
        InvocationRequest request = new InvocationRequestBuilder()
                .setGoals("validate")
                .setMavenRepoLocalPath(tempDir.toAbsolutePath().toString())
                .addChannelManifestGav(new MavenCoordinates("org.first", "m1", "1.0"))
                .addChannelManifestGav(new MavenCoordinates("org.second", "m2", "2.0"))
                .build();

        Properties props = request.getProperties();
        assertEquals("org.first", props.getProperty("testsuite.channel.manifest.groupId"));
        assertEquals("m1", props.getProperty("testsuite.channel.manifest.artifactId"));
        assertEquals("org.second", props.getProperty("testsuite.channel.manifest1.groupId"));
        assertEquals("m2", props.getProperty("testsuite.channel.manifest1.artifactId"));
    }

    // --- Galleon pack GAV properties ---

    /**
     * setGalleonPackGav sets the galleon pack properties on the built request.
     * Passes when the properties match the provided coordinates.
     */
    @Test
    void galleonPackGavSetsProperties() {
        InvocationRequest request = minimalBuilder()
                .setGalleonPackGav(new MavenCoordinates("org.test", "pack", "3.0"))
                .build();

        Properties props = request.getProperties();
        assertEquals("org.test", props.getProperty("testsuite.galleon.pack.groupId"));
        assertEquals("pack", props.getProperty("testsuite.galleon.pack.artifactId"));
        assertEquals("3.0", props.getProperty("testsuite.galleon.pack.version"));
    }

    /**
     * setEeGalleonPackGav sets the ee-galleon-pack properties on the built request.
     * Passes when the properties match the provided coordinates.
     */
    @Test
    void eeGalleonPackGavSetsProperties() {
        InvocationRequest request = minimalBuilder()
                .setEeGalleonPackGav(new MavenCoordinates("org.test", "ee-pack", "4.0"))
                .build();

        Properties props = request.getProperties();
        assertEquals("org.test", props.getProperty("testsuite.ee-galleon-pack.groupId"));
        assertEquals("ee-pack", props.getProperty("testsuite.ee-galleon-pack.artifactId"));
        assertEquals("4.0", props.getProperty("testsuite.ee-galleon-pack.version"));
    }

    /**
     * setCloudGalleonPackGav sets the cloud-galleon-pack properties on the built request.
     * Passes when the properties match the provided coordinates.
     */
    @Test
    void cloudGalleonPackGavSetsProperties() {
        InvocationRequest request = minimalBuilder()
                .setCloudGalleonPackGav(new MavenCoordinates("org.test", "cloud-pack", "5.0"))
                .build();

        Properties props = request.getProperties();
        assertEquals("org.test", props.getProperty("testsuite.cloud-galleon-pack.groupId"));
        assertEquals("cloud-pack", props.getProperty("testsuite.cloud-galleon-pack.artifactId"));
        assertEquals("5.0", props.getProperty("testsuite.cloud-galleon-pack.version"));
    }

    // --- Maven plugin GAV properties ---

    /**
     * setMavenPluginGav sets the wildfly-maven-plugin properties on the built request.
     * Passes when the properties match the provided coordinates.
     */
    @Test
    void mavenPluginGavSetsProperties() {
        InvocationRequest request = minimalBuilder()
                .setMavenPluginGav(new MavenCoordinates("org.wildfly.plugins", "wildfly-maven-plugin", "5.0"))
                .build();

        Properties props = request.getProperties();
        assertEquals("org.wildfly.plugins", props.getProperty("testsuite.wildfly-maven-plugin.groupId"));
        assertEquals("wildfly-maven-plugin", props.getProperty("testsuite.wildfly-maven-plugin.artifactId"));
        assertEquals("5.0", props.getProperty("testsuite.wildfly-maven-plugin.version"));
    }

    // --- WildFly JAR plugin properties ---

    /**
     * setWildflyJarMavenPluginVersion sets the plugin version property.
     * Passes when the property matches.
     */
    @Test
    void wildflyJarPluginVersionSetsProperty() {
        InvocationRequest request = minimalBuilder()
                .setWildflyJarMavenPluginVersion("10.0.0.Final")
                .build();

        assertEquals("10.0.0.Final", request.getProperties().getProperty("version.org.wildfly.jar.plugin"));
    }

    /**
     * setWildflyJarMavenPluginDevMode adds the "dev" property.
     * Passes when the "dev" property is present.
     */
    @Test
    void devModeSetsDevProperty() {
        InvocationRequest request = minimalBuilder()
                .setWildflyJarMavenPluginDevMode()
                .build();

        assertTrue(request.getProperties().containsKey("dev"));
    }

    // --- Dump original artifacts ---

    /**
     * setDumpOriginalArtifacts adds the dump property set to "true".
     * Passes when the property is present and set to "true".
     */
    @Test
    void dumpOriginalArtifactsSetsProperty() {
        InvocationRequest request = minimalBuilder()
                .setDumpOriginalArtifacts()
                .build();

        assertEquals("true", request.getProperties().getProperty("bootable.jar.dump.original.artifacts"));
    }

    // --- Custom properties ---

    /**
     * setProperty adds arbitrary key-value pairs to the built request's properties.
     * Passes when the custom property is present with the correct value.
     */
    @Test
    void customPropertiesAreSetOnRequest() {
        InvocationRequest request = minimalBuilder()
                .setProperty("my.custom.key", "my-value")
                .build();

        assertEquals("my-value", request.getProperties().getProperty("my.custom.key"));
    }

    /**
     * Multiple setProperty calls accumulate properties.
     * Passes when all custom properties are present.
     */
    @Test
    void multipleCustomPropertiesAccumulate() {
        InvocationRequest request = minimalBuilder()
                .setProperty("key1", "val1")
                .setProperty("key2", "val2")
                .build();

        Properties props = request.getProperties();
        assertEquals("val1", props.getProperty("key1"));
        assertEquals("val2", props.getProperty("key2"));
    }

    // --- Deprecated brew repo ---

    /**
     * setUseBrewRepo(true) adds the "use.brew.repo" property set to "true".
     * Passes when the property is present.
     */
    @SuppressWarnings("deprecation")
    @Test
    void useBrewRepoSetsProperty() {
        InvocationRequest request = minimalBuilder()
                .setUseBrewRepo(true)
                .build();

        assertEquals("true", request.getProperties().getProperty("use.brew.repo"));
    }

    // --- setChannelManifestGav replaces list ---

    /**
     * setChannelManifestGav clears any previously added manifests and uses the provided one.
     * Passes when only the replacement manifest's properties are set.
     */
    @Test
    void setChannelManifestGavReplacesExistingList() {
        InvocationRequest request = new InvocationRequestBuilder()
                .setGoals("validate")
                .setMavenRepoLocalPath(tempDir.toAbsolutePath().toString())
                .addChannelManifestGav(new MavenCoordinates("org.old", "old", "1.0"))
                .setChannelManifestGav(new MavenCoordinates("org.new", "replaced", "2.0"))
                .build();

        Properties props = request.getProperties();
        assertEquals("org.new", props.getProperty("testsuite.channel.manifest.groupId"));
        assertEquals("replaced", props.getProperty("testsuite.channel.manifest.artifactId"));
        assertNull(props.getProperty("testsuite.channel.manifest1.groupId"));
    }

    // --- setChannelManifestGavs replaces list ---

    /**
     * setChannelManifestGavs replaces the entire list with the provided one.
     * Passes when properties reflect the new list.
     */
    @Test
    void setChannelManifestGavsReplacesList() {
        InvocationRequest request = new InvocationRequestBuilder()
                .setGoals("validate")
                .setMavenRepoLocalPath(tempDir.toAbsolutePath().toString())
                .addChannelManifestGav(new MavenCoordinates("org.old", "old", "1.0"))
                .setChannelManifestGavs(Collections.singletonList(
                        new MavenCoordinates("org.replaced", "new-manifest", "3.0")))
                .build();

        Properties props = request.getProperties();
        assertEquals("org.replaced", props.getProperty("testsuite.channel.manifest.groupId"));
        assertEquals("new-manifest", props.getProperty("testsuite.channel.manifest.artifactId"));
    }

    private InvocationRequestBuilder minimalBuilder() {
        return new InvocationRequestBuilder()
                .setGoals("validate")
                .setMavenRepoLocalPath(tempDir.toAbsolutePath().toString())
                .addChannelManifestGav(new MavenCoordinates("", "", ""));
    }
}
