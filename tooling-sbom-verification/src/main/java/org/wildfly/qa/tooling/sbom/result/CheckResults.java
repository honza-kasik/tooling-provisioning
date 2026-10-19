/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.result;

import java.nio.file.Path;
import java.util.List;

/**
 * Factory for the known check categories and the {@link CheckResult results} of the checks that use them.
 * Keeps the wording of the issues in one place. Compare categories against these constants, not against
 * literal strings.
 */
public final class CheckResults {

    /** Whether the installation has a valid SBOM, and whether that is what was expected. */
    public static final String SBOM = "SBOM";

    /** Whether the installation has a valid manifest, and whether that is what was expected. */
    public static final String MANIFEST = "Manifest";

    /** Whether every path of the installation could be read when looking for JARs. */
    public static final String INSTALLED_JARS = "Installed JARs";

    /** Whether the {@code wildfly.zip} of a bootable JAR can be extracted. */
    public static final String WILDFLY_ZIP = "wildfly.zip";

    /** Whether every component declared in the SBOM is present on disk. */
    public static final String SBOM_VS_DISK = "SBOM vs disk";

    /** Whether every JAR on disk is declared in the SBOM. */
    public static final String DISK_VS_SBOM = "Disk vs SBOM";

    /** Whether every installed component of the SBOM is in the manifest with the same version. */
    public static final String SBOM_VS_MANIFEST = "SBOM vs manifest";

    /** Whether the shaded (POM-derived) artifacts of the SBOM meet a user-supplied expectation. */
    public static final String SHADED = "Shaded artifacts";

    private CheckResults() {
    }

    /** @return a passed result of the given category. */
    public static CheckResult passed(String category) {
        return new CheckResult(category, List.of());
    }

    /** @return a failed {@link #SBOM} result: an SBOM was expected but is missing. */
    public static CheckResult sbomMissing(Path root) {
        return new CheckResult(SBOM, List.of("no sbom.cdx.json or sbom.cdx.json.gz found in " + root));
    }

    /** @return a failed {@link #SBOM} result: no SBOM was expected but one exists. */
    public static CheckResult sbomUnexpected(Path sbomFile) {
        return new CheckResult(SBOM, List.of("unexpected SBOM found: " + sbomFile));
    }

    /** @return a failed {@link #SBOM} result: the SBOM exists but cannot be read or is not valid CycloneDX. */
    public static CheckResult sbomInvalid(Path sbomFile, String cause) {
        return new CheckResult(SBOM, List.of("invalid " + sbomFile + ": " + cause));
    }

    /** @return a failed {@link #SBOM} result with the given message, e.g. a failed user-supplied check. */
    public static CheckResult sbomCheckFailed(String message) {
        return new CheckResult(SBOM, List.of(message));
    }

    /** @return a failed {@link #MANIFEST} result: a manifest was expected but is missing. */
    public static CheckResult manifestMissing(Path root) {
        return new CheckResult(MANIFEST, List.of("no .installation/manifest.yaml found in " + root));
    }

    /** @return a failed {@link #MANIFEST} result: no manifest was expected but one exists. */
    public static CheckResult manifestUnexpected(Path manifestFile) {
        return new CheckResult(MANIFEST, List.of("unexpected manifest found: " + manifestFile));
    }

    /** @return a failed {@link #MANIFEST} result: the manifest exists but is not a valid channel manifest. */
    public static CheckResult manifestInvalid(Path manifestFile, String cause) {
        return new CheckResult(MANIFEST, List.of("invalid " + manifestFile + ": " + cause));
    }

    /** @return a failed {@link #MANIFEST} result with the given message, e.g. a failed user-supplied check. */
    public static CheckResult manifestCheckFailed(String message) {
        return new CheckResult(MANIFEST, List.of(message));
    }

    /**
     * @return an {@link #INSTALLED_JARS} result with one issue per path the scan could not read; passed
     *         when the list is empty
     */
    public static CheckResult unreadablePaths(List<String> unreadable) {
        return new CheckResult(INSTALLED_JARS, unreadable.stream().map(path -> "cannot read " + path).toList());
    }

    /** @return a failed {@link #WILDFLY_ZIP} result: the archive is missing or could not be extracted. */
    public static CheckResult extractionFailed(Path zip, String cause) {
        return new CheckResult(WILDFLY_ZIP, List.of("cannot extract " + zip + ": " + cause));
    }

    /** @return a failed {@link #INSTALLED_JARS} result: the installation tree could not be walked at all. */
    public static CheckResult scanFailed(Path root, String cause) {
        return new CheckResult(INSTALLED_JARS, List.of("cannot scan " + root + ": " + cause));
    }

    /**
     * @return a {@link #SBOM_VS_DISK} result with one issue per SBOM component that is missing on disk;
     *         passed when the list is empty
     */
    public static CheckResult sbomVsDisk(List<String> missing) {
        return new CheckResult(SBOM_VS_DISK, missing);
    }

    /**
     * @return a {@link #DISK_VS_SBOM} result with one issue per JAR on disk that the SBOM does not declare;
     *         passed when the list is empty
     */
    public static CheckResult diskVsSbom(List<String> undeclared) {
        return new CheckResult(DISK_VS_SBOM, undeclared);
    }

    /**
     * @return a {@link #SBOM_VS_MANIFEST} result with one issue per SBOM component the manifest lacks or
     *         lists with another version; passed when the list is empty
     */
    public static CheckResult sbomVsManifest(List<String> issues) {
        return new CheckResult(SBOM_VS_MANIFEST, issues);
    }

    /** @return a failed {@link #SHADED} result with the given message, e.g. a failed user-supplied check. */
    public static CheckResult shadedCheckFailed(String message) {
        return new CheckResult(SHADED, List.of(message));
    }

    /**
     * @return a failed result of the given category: the check could not run because what it compares is
     *         unavailable. The cause is reported by the check of that input, not repeated here.
     */
    public static CheckResult unavailable(String category, String input) {
        return new CheckResult(category, List.of("not checked: " + input + " is unavailable"));
    }
}
