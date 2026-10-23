/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.qa.tooling.sbom.result.CheckCategory;
import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.result.ShadedResult;
import org.wildfly.qa.tooling.sbom.scanner.InstallationScanner;
import org.wildfly.qa.tooling.sbom.verification.fluent.WithDiskChecks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link SbomVerifier} against the community WildFly distribution unpacked by the
 * shared {@code integration-tests} profile. Its installation is given by {@code wildfly.home}.
 * WildFly builds package the SBOM as {@code sbom.cdx.json.gz}, which is the path the tests pass to the verifier.
 * Run with:
 * <pre>
 *     mvn verify -pl tooling-sbom-verification -Pintegration-tests
 * </pre>
 *
 * <p>No component or JAR counts are hard-coded. The CPE is not checked because the community SBOM does not
 * carry {@code metadata.component.cpe}.</p>
 */
class SbomVerifierIT {

    /** Where WildFly builds put the (gzipped) SBOM, relative to the installation. */
    private static final Path SBOM_FILE = Path.of("sbom.cdx.json.gz");

    private static Path home() {
        String home = System.getProperty("wildfly.home");
        assertNotNull(home, "wildfly.home system property must be set");
        Path path = Paths.get(home);
        assertTrue(Files.isDirectory(path), "WildFly home must exist: " + path);
        return path;
    }

    // ---- the whole chain ----------------------------------------------------

    @Test
    void allChecksPass() {
        List<CheckResult> results = verifier()
                .withSbom()
                .withManifest()
                .withSbomMatchingManifest()
                .withDiskChecks()
                .verify();

        assertAllPassed(results);
        assertCategories(results, CheckCategory.INSTALLED_JARS, CheckCategory.SBOM, CheckCategory.MANIFEST,
                CheckCategory.SBOM_VS_MANIFEST, CheckCategory.SBOM_VS_DISK, CheckCategory.DISK_VS_SBOM);
    }

    // ---- SBOM ----------------------------------------------------------------

    @Test
    void sbomIsValidAndDescribesTheProduct() {
        List<CheckResult> results = verifier()
                .withSbom()
                .withBomCheck(bom -> bom.getBomFormat() != null && bom.getSpecVersion() != null,
                        "bomFormat or specVersion is missing")
                .withBomCheck(bom -> bom.getMetadata().getComponent().getName() != null,
                        "metadata.component.name is missing")
                .withBomCheck(bom -> !bom.getComponents().isEmpty(), "no components in the SBOM")
                .verify();

        assertAllPassed(results);
    }

    @Test
    void sbomNamesTheExpectedProduct() {
        List<CheckResult> results = verifier()
                .withSbom()
                .withBomCheck(bom -> "WildFly".equals(bom.getMetadata().getComponent().getName()),
                        "metadata.component.name is not WildFly")
                .verify();

        assertAllPassed(results);
    }

    @Test
    void failedBomCheckIsReportedWithItsMessage() {
        List<CheckResult> results = verifier()
                .withSbom()
                .withBomCheck(bom -> false, "expected failure")
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckCategory.SBOM, failed.category());
        assertEquals(List.of("expected failure"), failed.issues());
    }

    /**
     * The SBOMs currently put {@code confidence} on the identity entry, but the schema also requires it in
     * every {@code methods[]} entry. Until the generator is fixed, that is the only schema error allowed;
     * any other one fails this test. It also passes once the SBOM is fully valid.
     */
    @Test
    void schemaValidationFindsNothingButTheKnownIdentityEvidenceErrors() {
        List<CheckResult> results = verifier()
                .withSbom()
                .withSchemaValidation()
                .verify();

        List<CheckResult> failed = results.stream().filter(r -> !r.passed()).toList();
        assertTrue(failed.size() <= 1, "Expected at most the schema check to fail: " + failed);
        failed.forEach(result -> {
            assertEquals(CheckCategory.SBOM, result.category());
            // the first issue is the header with the file and the totals; the others are the error groups
            List<String> unknown = result.issues().stream().skip(1)
                    .filter(issue -> !issue.contains("/evidence/identity"))
                    .toList();
            assertTrue(unknown.isEmpty(), "Unexpected schema errors: " + unknown);
        });
    }

    @Test
    void schemaValidationIsOffUnlessAskedFor() {
        List<CheckResult> results = verifier().withSbom().verify();

        assertAllPassed(results);
    }

    @Test
    void installationWithSbomFailsWhenNoSbomIsExpected() {
        List<CheckResult> results = verifier().withoutSbom().verify();

        assertEquals(CheckCategory.SBOM, onlyFailure(results).category());
    }

    // ---- manifest ------------------------------------------------------------

    @Test
    void manifestHasStreams() {
        List<CheckResult> results = verifier()
                .withSbom()
                .withManifest()
                .withManifestCheck(manifest -> !manifest.getStreams().isEmpty(), "the manifest has no streams")
                .verify();

        assertAllPassed(results);
    }

    @Test
    void installationWithManifestFailsWhenNoManifestIsExpected() {
        List<CheckResult> results = verifier().withSbom().withoutManifest().verify();

        assertEquals(CheckCategory.MANIFEST, onlyFailure(results).category());
    }

    // ---- disk checks and shaded artifacts ----------------------------------------

    @Test
    void shadedArtifactsAreNotACheckUnlessAskedFor() {
        List<CheckResult> results = verifier()
                .withSbom().withManifest().withDiskChecks().verify();

        assertFalse(results.stream().anyMatch(r -> CheckCategory.SHADED.equals(r.category())),
                "No shaded check expected when withShadedCheck is not called: " + results);
    }

    @Test
    void shadedCheckReceivesTheUntrackedShadedArtifacts() {
        AtomicReference<ShadedResult> seen = new AtomicReference<>();

        List<CheckResult> results = verifier()
                .withSbom().withManifest().withDiskChecks()
                .withShadedCheck(shaded -> {
                    seen.set(shaded);
                    return true;
                }, "unreachable")
                .verify();

        assertAllPassed(results);
        ShadedResult shaded = seen.get();
        assertNotNull(shaded, "The shaded predicate must be called");
        assertEquals(shaded.byParentPurl().values().stream().mapToInt(List::size).sum(), shaded.totalArtifacts());
    }

    @Test
    void failedShadedCheckIsReportedWithItsMessage() {
        List<CheckResult> results = verifier()
                .withSbom().withManifest().withDiskChecks()
                .withShadedCheck(shaded -> false, "expected failure")
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckCategory.SHADED, failed.category());
        assertEquals(List.of("expected failure"), failed.issues());
    }

    // ---- excluded scan paths ---------------------------------------------------

    @Test
    void excludedScanPathThatDoesNotExistChangesNothing() {
        List<Path> excluded = List.of(InstallationScanner.DEFAULT_EXCLUDED.get(0), Path.of("no/such/dir"));

        List<CheckResult> results = new SbomVerifier(home(), SBOM_FILE, excluded)
                .withSbom().withManifest().withDiskChecks()
                .verify();

        assertAllPassed(results);
    }

    @Test
    void excludingTheModulesDirectoryMakesTheSbomComponentsThereMissing() {
        List<Path> excluded = List.of(InstallationScanner.DEFAULT_EXCLUDED.get(0), Path.of("modules"));

        List<CheckResult> results = new SbomVerifier(home(), SBOM_FILE, excluded)
                .withSbom().withManifest().withDiskChecks()
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckCategory.SBOM_VS_DISK, failed.category());
    }

    // ---- fat JAR names -----------------------------------------------------------

    @Test
    void explicitDefaultFatJarNamesChangeNothing() {
        List<CheckResult> results = verifier()
                .withSbom().withManifest().withDiskChecks()
                .withFatJarNames(WithDiskChecks.DEFAULT_FAT_JAR_NAMES)
                .withGenericFatJars(WithDiskChecks.DEFAULT_GENERIC_FAT_JARS)
                .verify();

        assertAllPassed(results);
    }

    @Test
    void withoutTheFatJarNamesTheFixedNameJarsAreMissing() {
        List<CheckResult> results = verifier()
                .withSbom().withManifest().withDiskChecks()
                .withFatJarNames(Map.of())
                .verify();

        // the name lookup cannot find jboss-modules.jar and launcher.jar, but they are still on disk at the
        // location the SBOM declares, so the other direction has nothing to report
        CheckResult failed = onlyFailure(results);
        assertEquals(CheckCategory.SBOM_VS_DISK, failed.category());
        assertFalse(failed.issues().isEmpty(), failed.toString());
        assertTrue(failed.issues().stream().allMatch(i -> i.contains("[expected: ")), failed.toString());
    }

    @Test
    void withoutTheGenericFatJarsTheAssembledJarsAreUndeclared() {
        List<CheckResult> results = verifier()
                .withSbom().withManifest().withDiskChecks()
                .withGenericFatJars(Set.of())
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckCategory.DISK_VS_SBOM, failed.category());
        assertEquals(WithDiskChecks.DEFAULT_GENERIC_FAT_JARS.size(), failed.issues().size(), failed.toString());
    }

    /** A fat JAR on disk and a Maven component; {@code generic} adds the pkg:generic entry that declares the JAR. */
    private static String fatJarSbom(boolean generic) {
        String genericComponent = """
                ,{"type":"library","name":"jboss-client","version":"1",
                  "purl":"pkg:generic/jboss-client@1",
                  "evidence":{"occurrences":[{"location":"bin/client/jboss-client.jar"}]}}
                """;
        return """
                {"bomFormat":"CycloneDX","specVersion":"1.6","version":1,"components":[
                  {"type":"library","group":"org.example","name":"lib","version":"1",
                   "purl":"pkg:maven/org.example/lib@1","evidence":{"occurrences":[{"location":"modules/lib-1.jar"}]}}
                """ + (generic ? genericComponent : "") + "]}";
    }

    private static Path fatJarInstallation(Path root, boolean generic) throws IOException {
        Files.createDirectories(root.resolve("modules"));
        Files.createDirectories(root.resolve("bin/client"));
        Files.writeString(root.resolve("modules/lib-1.jar"), "x");
        Files.writeString(root.resolve("bin/client/jboss-client.jar"), "x");
        Files.writeString(root.resolve("sbom.cdx.json"), fatJarSbom(generic));
        return root;
    }

    @Test
    void aListedFatJarIsAcceptedWhenTheSbomDeclaresItAsGeneric(@TempDir Path root) throws IOException {
        fatJarInstallation(root, true);

        List<CheckResult> results = new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify();

        assertAllPassed(results);
    }

    @Test
    void aListedFatJarTheSbomDoesNotDeclareAsGenericIsReportedAsUndeclared(@TempDir Path root) throws IOException {
        fatJarInstallation(root, false);

        List<CheckResult> results = new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckCategory.DISK_VS_SBOM, failed.category());
        assertEquals(List.of("bin/client/jboss-client.jar"), failed.issues());
    }

    @Test
    void aGenericFatJarTheSbomDeclaresButIsMissingOnDiskIsReported(@TempDir Path root) throws IOException {
        fatJarInstallation(root, true);
        Files.delete(root.resolve("bin/client/jboss-client.jar"));

        List<CheckResult> results = new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckCategory.SBOM_VS_DISK, failed.category());
        assertEquals(1, failed.issues().size(), failed.toString());
        assertTrue(failed.issues().get(0).contains("pkg:generic/jboss-client@1")
                && failed.issues().get(0).contains("bin/client/jboss-client.jar"), failed.toString());
    }

    @Test
    void aFatJarNameInAnotherDirectoryIsNotAcceptedAsGeneric(@TempDir Path root) throws IOException {
        fatJarInstallation(root, true);
        Files.createDirectories(root.resolve("elsewhere"));
        Files.writeString(root.resolve("elsewhere/jboss-client.jar"), "x");

        List<CheckResult> results = new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckCategory.DISK_VS_SBOM, failed.category());
        assertEquals(List.of("elsewhere/jboss-client.jar"), failed.issues());
    }

    /** A Maven component that is installed as {@code lib.jar} instead of {@code lib-1.jar}. */
    private static Path fixedNameInstallation(Path root, String location) throws IOException {
        Files.createDirectories(root.resolve("bin"));
        Files.writeString(root.resolve("bin/lib.jar"), "x");
        Files.writeString(root.resolve("sbom.cdx.json"), jarSbom(location));
        return root;
    }

    @Test
    void aFatJarNameMakesTheJarAtThatPathBeFoundAndDeclared(@TempDir Path root) throws IOException {
        fixedNameInstallation(root, "bin/lib.jar");

        assertAllPassed(new SbomVerifier(root, Path.of("sbom.cdx.json"), InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .withFatJarNames(Map.of("lib", "bin/lib.jar"))
                .verify());
    }

    @Test
    void withoutAFatJarNameTheFixedNameJarIsNotFoundByItsVersionedName(@TempDir Path root) throws IOException {
        fixedNameInstallation(root, "bin/lib.jar");

        List<CheckResult> results = new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify();

        assertEquals(List.of("pkg:maven/org.example/lib@1 [expected: lib-1.jar]"),
                issuesOf(results, CheckCategory.SBOM_VS_DISK));
        // the SBOM declares bin/lib.jar as a location, which is all the other direction needs
        assertEquals(List.of(), issuesOf(results, CheckCategory.DISK_VS_SBOM));
    }

    @Test
    void aFatJarNameDeclaresOnlyTheJarAtItsPath(@TempDir Path root) throws IOException {
        fixedNameInstallation(root, "bin/lib.jar");
        Files.createDirectories(root.resolve("elsewhere"));
        Files.writeString(root.resolve("elsewhere/lib.jar"), "x");

        List<CheckResult> results = new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .withFatJarNames(Map.of("lib", "bin/lib.jar"))
                .verify();

        assertEquals(List.of(), issuesOf(results, CheckCategory.SBOM_VS_DISK));
        assertEquals(List.of("elsewhere/lib.jar"), issuesOf(results, CheckCategory.DISK_VS_SBOM));
    }

    @Test
    void twoUndeclaredCopiesOfAJarAreTwoIssues(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("modules"));
        Files.createDirectories(root.resolve("one"));
        Files.createDirectories(root.resolve("two"));
        Files.writeString(root.resolve("modules/lib-1.jar"), "x");
        Files.writeString(root.resolve("one/lib-1.jar"), "x");
        Files.writeString(root.resolve("two/lib-1.jar"), "x");
        Files.writeString(root.resolve("sbom.cdx.json"), jarSbom("modules/lib-1.jar"));

        List<CheckResult> results = new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify();

        assertEquals(Set.of("one/lib-1.jar", "two/lib-1.jar"),
                Set.copyOf(issuesOf(results, CheckCategory.DISK_VS_SBOM)));
        assertEquals(2, issuesOf(results, CheckCategory.DISK_VS_SBOM).size());
    }

    @Test
    void aJarIsDeclaredByEachOfSeveralLocations(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("modules"));
        Files.createDirectories(root.resolve("other"));
        Files.writeString(root.resolve("modules/lib-1.jar"), "x");
        Files.writeString(root.resolve("other/lib-1.jar"), "x");
        Files.writeString(root.resolve("sbom.cdx.json"), jarSbom("modules/lib-1.jar", "other/lib-1.jar"));

        assertAllPassed(new SbomVerifier(root, Path.of("sbom.cdx.json"), InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify());
    }

    @Test
    void aSecondCopyOfADeclaredMavenJarIsReportedAtItsPath(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("modules"));
        Files.createDirectories(root.resolve("elsewhere"));
        Files.writeString(root.resolve("modules/lib-1.jar"), "x");
        Files.writeString(root.resolve("elsewhere/lib-1.jar"), "x");
        Files.writeString(root.resolve("sbom.cdx.json"), jarSbom("modules/lib-1.jar"));

        CheckResult failed = onlyFailure(new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify());

        assertEquals(CheckCategory.DISK_VS_SBOM, failed.category());
        assertEquals(List.of("elsewhere/lib-1.jar"), failed.issues());
    }

    // ---- every occurrence of a Maven component -----------------------------------

    /** A Maven JAR that the SBOM places at the given locations. */
    private static String jarSbom(String... locations) {
        String occurrences = java.util.Arrays.stream(locations)
                .map(l -> "{\"location\":\"" + l + "\"}")
                .collect(Collectors.joining(","));
        return "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\",\"version\":1,\"components\":["
                + "{\"type\":\"library\",\"group\":\"org.example\",\"name\":\"lib\",\"version\":\"1\","
                + "\"purl\":\"pkg:maven/org.example/lib@1\",\"evidence\":{\"occurrences\":[" + occurrences + "]}}]}";
    }

    private static List<CheckResult> verifyJar(Path root, String... locations) throws IOException {
        Files.createDirectories(root.resolve("modules"));
        Files.writeString(root.resolve("modules/lib-1.jar"), "x");
        Files.writeString(root.resolve("sbom.cdx.json"), jarSbom(locations));
        return new SbomVerifier(root, Path.of("sbom.cdx.json"), InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify();
    }

    @Test
    void aMavenJarWhoseOccurrencesAllExistPasses(@TempDir Path root) throws IOException {
        assertAllPassed(verifyJar(root, "modules/lib-1.jar", "modules"));
    }

    @Test
    void aMissingSecondOccurrenceOfAMavenJarIsReported(@TempDir Path root) throws IOException {
        CheckResult failed = onlyFailure(verifyJar(root, "modules/lib-1.jar", "modules/gone"));

        assertEquals(CheckCategory.SBOM_VS_DISK, failed.category());
        assertEquals(List.of("pkg:maven/org.example/lib@1 [file missing: modules/gone]"), failed.issues());
    }

    @Test
    void aJarFoundElsewhereThanItsOccurrenceIsReportedInBothDirections(@TempDir Path root) throws IOException {
        List<CheckResult> results = verifyJar(root, "other/lib-1.jar");

        assertEquals(List.of("pkg:maven/org.example/lib@1 [file missing: other/lib-1.jar]"),
                issuesOf(results, CheckCategory.SBOM_VS_DISK));
        assertEquals(List.of("modules/lib-1.jar"), issuesOf(results, CheckCategory.DISK_VS_SBOM));
    }

    @Test
    void aMavenJarWithoutALocationIsReportedEvenIfItIsOnDisk(@TempDir Path root) throws IOException {
        List<CheckResult> results = verifyJar(root);

        List<String> issues = issuesOf(results, CheckCategory.SBOM_VS_DISK);
        assertEquals(1, issues.size(), results.toString());
        assertTrue(issues.get(0).startsWith("pkg:maven/org.example/lib@1 [no location declared"),
                results.toString());
        // it declares no location, so the JAR on disk is not declared either
        assertEquals(List.of("modules/lib-1.jar"), issuesOf(results, CheckCategory.DISK_VS_SBOM));
    }

    @Test
    void aMissingJarIsReportedOnceNotOncePerLookup(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("modules"));
        Files.writeString(root.resolve("sbom.cdx.json"), jarSbom("modules/lib-1.jar"));

        CheckResult failed = onlyFailure(new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify());

        assertEquals(List.of("pkg:maven/org.example/lib@1 [expected: lib-1.jar]"), failed.issues());
    }

    @Test
    void aGenericComponentWithoutALocationIsReported(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("sbom.cdx.json"), """
                {"bomFormat":"CycloneDX","specVersion":"1.6","version":1,"components":[
                  {"type":"library","name":"tool","version":"1","purl":"pkg:generic/tool@1"}]}
                """);

        CheckResult failed = onlyFailure(new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify());

        assertEquals(CheckCategory.SBOM_VS_DISK, failed.category());
        assertEquals(1, failed.issues().size(), failed.toString());
        assertTrue(failed.issues().get(0).startsWith("pkg:generic/tool@1 [no location declared"), failed.toString());
    }

    @Test
    void aNonJarMavenComponentWithoutALocationIsReported(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("sbom.cdx.json"), """
                {"bomFormat":"CycloneDX","specVersion":"1.6","version":1,"components":[
                  {"type":"library","group":"g","name":"conf","version":"1",
                   "purl":"pkg:maven/g/conf@1?classifier=version&type=txt"}]}
                """);

        CheckResult failed = onlyFailure(new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify());

        assertEquals(CheckCategory.SBOM_VS_DISK, failed.category());
        assertTrue(failed.issues().get(0).contains("[no location declared"), failed.toString());
    }

    @Test
    void aPomDerivedComponentWithoutALocationIsNotReported(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("sbom.cdx.json"), """
                {"bomFormat":"CycloneDX","specVersion":"1.6","version":1,"components":[
                  {"type":"library","group":"g","name":"shaded","version":"1","purl":"pkg:maven/g/shaded@1",
                   "evidence":{"identity":[{"field":"purl","confidence":1.0,
                     "methods":[{"technique":"manifest-analysis","value":"maven-pom-analysis"}]}]}}]}
                """);

        assertAllPassed(new SbomVerifier(root, Path.of("sbom.cdx.json"), InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify());
    }

    // ---- location of the SBOM ----------------------------------------------------

    @Test
    void aWrongSbomPathIsReportedAsMissing() {
        List<CheckResult> results = new SbomVerifier(home(), Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom()
                .verify();

        CheckResult failed = results.stream().filter(r -> !r.passed()).findFirst().orElseThrow();
        assertEquals(CheckCategory.SBOM, failed.category());
        assertTrue(failed.issues().get(0).contains("sbom.cdx.json"), failed.toString());
    }

    @Test
    void anAbsolutePathToAPlainJsonSbomIsUsedAsItIs(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve("my-sbom.json");
        try (InputStream in = new GZIPInputStream(Files.newInputStream(home().resolve(SBOM_FILE)))) {
            Files.copy(in, plain);
        }

        List<CheckResult> results = new SbomVerifier(home(), plain, InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withManifest().withDiskChecks()
                .verify();

        assertAllPassed(results);
    }

    @Test
    void withoutSbomChecksTheGivenPathOnly() {
        List<CheckResult> results = new SbomVerifier(home(), Path.of("no-such-sbom.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withoutSbom()
                .verify();

        assertAllPassed(results);
    }

    // ---- non-JAR components ------------------------------------------------------

    /** The shape of the feature pack's {@code version.txt} component, as in the real SBOMs. */
    private static final String VERSION_TXT_SBOM = """
            {"bomFormat":"CycloneDX","specVersion":"1.6","version":1,"components":[
              {"type":"library","group":"org.wildfly","name":"wildfly-ee-feature-pack-product-conf",
               "version":"42.0.0.Beta1",
               "purl":"pkg:maven/org.wildfly/wildfly-ee-feature-pack-product-conf@42.0.0.Beta1?classifier=version&type=txt",
               "evidence":{"occurrences":[{"location":"version.txt"}]}}]}
            """;

    @Test
    void aNonJarComponentIsCheckedAtItsLocation(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("sbom.cdx.json"), VERSION_TXT_SBOM);
        Files.writeString(root.resolve("version.txt"), "WildFly - Version 42.0.0.Beta1");

        List<CheckResult> results = new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify();

        assertAllPassed(results);
    }

    @Test
    void aMissingFileOfANonJarComponentIsReported(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("sbom.cdx.json"), VERSION_TXT_SBOM);

        List<CheckResult> results = new SbomVerifier(root, Path.of("sbom.cdx.json"),
                InstallationScanner.DEFAULT_EXCLUDED)
                .withSbom().withoutManifest().withDiskChecks()
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckCategory.SBOM_VS_DISK, failed.category());
        assertEquals(1, failed.issues().size(), failed.toString());
        assertTrue(failed.issues().get(0).contains("type=txt") && failed.issues().get(0).contains("version.txt"),
                failed.toString());
    }

    // ---- helpers ---------------------------------------------------------------

    /** A verifier of the community installation that skips only {@code .installation}. */
    private static SbomVerifier verifier() {
        return new SbomVerifier(home(), SBOM_FILE, InstallationScanner.DEFAULT_EXCLUDED);
    }

    private static void assertAllPassed(List<CheckResult> results) {
        assertFalse(results.isEmpty(), "Expected at least one check result");
        List<CheckResult> failed = results.stream().filter(r -> !r.passed()).toList();
        assertTrue(failed.isEmpty(), "Checks failed: " + failed);
    }

    private static void assertCategories(List<CheckResult> results, CheckCategory... expected) {
        assertEquals(Set.of(expected), results.stream().map(CheckResult::category).collect(Collectors.toSet()));
        assertEquals(expected.length, results.size());
    }

    private static List<String> issuesOf(List<CheckResult> results, CheckCategory category) {
        return results.stream().filter(r -> r.category() == category).findFirst().orElseThrow().issues();
    }

    private static CheckResult onlyFailure(List<CheckResult> results) {
        List<CheckResult> failed = results.stream().filter(r -> !r.passed()).toList();
        assertEquals(1, failed.size(), "Expected exactly one failed check: " + results);
        return failed.get(0);
    }
}
