/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.result.CheckResults;
import org.wildfly.qa.tooling.sbom.result.ShadedResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link SbomVerifier}, run against each {@link Flavour} of WildFly. The flavours differ
 * in the product name that the SBOM declares; everything else is expected to hold for all of them. The
 * installation of a flavour is the directory given by its system property, which the
 * {@code integration-tests} profile sets to the distribution unpacked from Maven. A flavour whose property is
 * not set is skipped. The SBOM may be packaged either as {@code sbom.cdx.json} or as
 * {@code sbom.cdx.json.gz}, as WildFly builds do. Run with:
 * <pre>
 *     mvn verify -pl tooling-sbom-verification -Pintegration-tests
 * </pre>
 *
 * <p>The assertions are independent of the WildFly version: no component or JAR counts are hard-coded. The
 * CPE is not checked, because the SBOMs of the community flavours do not carry
 * {@code metadata.component.cpe}.</p>
 */
class SbomVerifierIT {

    /** A flavour of WildFly: the product name its SBOM declares and where its installation is. */
    enum Flavour {
        WILDFLY("WildFly", "wildfly.home"),
        PREVIEW("WildFly Preview", "wildfly.preview.home"),
        EE("WildFly EE", "wildfly.ee.home");

        private final String productName;
        private final String homeProperty;

        Flavour(String productName, String homeProperty) {
            this.productName = productName;
            this.homeProperty = homeProperty;
        }

        /** @return the installation; the test is skipped if the flavour has none configured */
        Path home() {
            String home = System.getProperty(homeProperty);
            assumeTrue(home != null && !home.isBlank(), homeProperty + " system property is not set");
            Path path = Paths.get(home);
            assertTrue(path.toFile().isDirectory(), "The home of " + this + " must exist: " + path);
            return path;
        }

        @Override
        public String toString() {
            return productName;
        }
    }

    // ---- the whole chain ----------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void allChecksPass(Flavour flavour) {
        List<CheckResult> results = new SbomVerifier(flavour.home())
                .withSbom()
                .withManifest()
                .withSbomMatchingManifest()
                .withDiskChecks()
                .verify();

        assertAllPassed(results);
        assertCategories(results, CheckResults.INSTALLED_JARS, CheckResults.SBOM, CheckResults.MANIFEST,
                CheckResults.SBOM_VS_MANIFEST, CheckResults.SBOM_VS_DISK, CheckResults.DISK_VS_SBOM);
    }

    // ---- SBOM ----------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void sbomIsValidAndDescribesTheProduct(Flavour flavour) {
        List<CheckResult> results = new SbomVerifier(flavour.home())
                .withSbom()
                .withBomCheck(bom -> bom.getBomFormat() != null && bom.getSpecVersion() != null,
                        "bomFormat or specVersion is missing")
                .withBomCheck(bom -> bom.getMetadata().getComponent().getName() != null,
                        "metadata.component.name is missing")
                .withBomCheck(bom -> !bom.getComponents().isEmpty(), "no components in the SBOM")
                .verify();

        assertAllPassed(results);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void sbomNamesTheExpectedProduct(Flavour flavour) {
        List<CheckResult> results = new SbomVerifier(flavour.home())
                .withSbom()
                .withBomCheck(bom -> flavour.productName.equals(bom.getMetadata().getComponent().getName()),
                        "metadata.component.name is not " + flavour.productName)
                .verify();

        assertAllPassed(results);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void failedBomCheckIsReportedWithItsMessage(Flavour flavour) {
        List<CheckResult> results = new SbomVerifier(flavour.home())
                .withSbom()
                .withBomCheck(bom -> false, "expected failure")
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckResults.SBOM, failed.category());
        assertEquals(List.of("expected failure"), failed.issues());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void installationWithSbomFailsWhenNoSbomIsExpected(Flavour flavour) {
        List<CheckResult> results = new SbomVerifier(flavour.home()).withoutSbom().verify();

        assertEquals(CheckResults.SBOM, onlyFailure(results).category());
    }

    // ---- manifest ------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void manifestHasStreams(Flavour flavour) {
        List<CheckResult> results = new SbomVerifier(flavour.home())
                .withSbom()
                .withManifest()
                .withManifestCheck(manifest -> !manifest.getStreams().isEmpty(), "the manifest has no streams")
                .verify();

        assertAllPassed(results);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void installationWithManifestFailsWhenNoManifestIsExpected(Flavour flavour) {
        List<CheckResult> results = new SbomVerifier(flavour.home()).withSbom().withoutManifest().verify();

        assertEquals(CheckResults.MANIFEST, onlyFailure(results).category());
    }

    // ---- disk checks and shaded artifacts ----------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void shadedArtifactsAreNotACheckUnlessAskedFor(Flavour flavour) {
        List<CheckResult> results = new SbomVerifier(flavour.home())
                .withSbom().withManifest().withDiskChecks().verify();

        assertFalse(results.stream().anyMatch(r -> CheckResults.SHADED.equals(r.category())),
                "No shaded check expected when withShadedCheck is not called: " + results);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void shadedCheckReceivesTheUntrackedShadedArtifacts(Flavour flavour) {
        AtomicReference<ShadedResult> seen = new AtomicReference<>();

        List<CheckResult> results = new SbomVerifier(flavour.home())
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

    @ParameterizedTest(name = "{0}")
    @EnumSource(Flavour.class)
    void failedShadedCheckIsReportedWithItsMessage(Flavour flavour) {
        List<CheckResult> results = new SbomVerifier(flavour.home())
                .withSbom().withManifest().withDiskChecks()
                .withShadedCheck(shaded -> false, "expected failure")
                .verify();

        CheckResult failed = onlyFailure(results);
        assertEquals(CheckResults.SHADED, failed.category());
        assertEquals(List.of("expected failure"), failed.issues());
    }

    // ---- helpers ---------------------------------------------------------------

    private static void assertAllPassed(List<CheckResult> results) {
        assertFalse(results.isEmpty(), "Expected at least one check result");
        List<CheckResult> failed = results.stream().filter(r -> !r.passed()).toList();
        assertTrue(failed.isEmpty(), "Checks failed: " + failed);
    }

    private static void assertCategories(List<CheckResult> results, String... expected) {
        assertEquals(Set.of(expected), results.stream().map(CheckResult::category).collect(Collectors.toSet()));
        assertEquals(expected.length, results.size());
    }

    private static CheckResult onlyFailure(List<CheckResult> results) {
        List<CheckResult> failed = results.stream().filter(r -> !r.passed()).toList();
        assertEquals(1, failed.size(), "Expected exactly one failed check: " + results);
        return failed.get(0);
    }
}
