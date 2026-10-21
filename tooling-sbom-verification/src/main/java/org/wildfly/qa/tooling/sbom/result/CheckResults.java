/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.result;

import org.cyclonedx.exception.ParseException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.wildfly.qa.tooling.sbom.result.CheckCategory.DISK_VS_SBOM;
import static org.wildfly.qa.tooling.sbom.result.CheckCategory.INSTALLED_JARS;
import static org.wildfly.qa.tooling.sbom.result.CheckCategory.MANIFEST;
import static org.wildfly.qa.tooling.sbom.result.CheckCategory.SBOM;
import static org.wildfly.qa.tooling.sbom.result.CheckCategory.SBOM_VS_DISK;
import static org.wildfly.qa.tooling.sbom.result.CheckCategory.SBOM_VS_MANIFEST;
import static org.wildfly.qa.tooling.sbom.result.CheckCategory.SHADED;
import static org.wildfly.qa.tooling.sbom.result.CheckCategory.WILDFLY_ZIP;

/**
 * Factory for the {@link CheckResult results} of the checks, by {@link CheckCategory}. Keeps the wording of
 * the issues in one place.
 */
public final class CheckResults {

    private CheckResults() {
    }

    /** @return a passed result of the given category. */
    public static CheckResult passed(CheckCategory category) {
        return new CheckResult(category, List.of());
    }

    /** @return a failed {@link CheckCategory#SBOM} result: an SBOM was expected but is missing. */
    public static CheckResult sbomMissing(Path sbomFile) {
        return new CheckResult(SBOM, List.of("SBOM not found: " + sbomFile));
    }

    /** @return a failed {@link CheckCategory#SBOM} result: no SBOM was expected but one exists. */
    public static CheckResult sbomUnexpected(Path sbomFile) {
        return new CheckResult(SBOM, List.of("unexpected SBOM found: " + sbomFile));
    }

    /**
     * @return a failed {@link CheckCategory#SBOM} result: the SBOM exists but cannot be read or is not valid
     *         CycloneDX.
     */
    public static CheckResult sbomInvalid(Path sbomFile, String cause) {
        return new CheckResult(SBOM, List.of("invalid " + sbomFile + ": " + cause));
    }

    /**
     * @return a failed {@link CheckCategory#SBOM} result: the SBOM does not conform to the CycloneDX JSON
     *         schema. The first issue names the file and the number of errors, the others are the messages
     *         of the errors.
     */
    public static CheckResult sbomSchemaInvalid(Path sbomFile, List<ParseException> errors) {
        List<String> issues = new ArrayList<>();
        issues.add(sbomFile + " does not conform to the CycloneDX schema: " + errors.size() + " errors");
        errors.forEach(error -> issues.add(error.getMessage()));
        return new CheckResult(SBOM, issues);
    }

    /**
     * @return a failed {@link CheckCategory#SBOM} result with the given message, e.g. a failed user-supplied
     *         check.
     */
    public static CheckResult sbomCheckFailed(String message) {
        return new CheckResult(SBOM, List.of(message));
    }

    /** @return a failed {@link CheckCategory#MANIFEST} result: a manifest was expected but is missing. */
    public static CheckResult manifestMissing(Path root) {
        return new CheckResult(MANIFEST, List.of("no .installation/manifest.yaml found in " + root));
    }

    /** @return a failed {@link CheckCategory#MANIFEST} result: no manifest was expected but one exists. */
    public static CheckResult manifestUnexpected(Path manifestFile) {
        return new CheckResult(MANIFEST, List.of("unexpected manifest found: " + manifestFile));
    }

    /**
     * @return a failed {@link CheckCategory#MANIFEST} result: the manifest exists but is not a valid channel
     *         manifest.
     */
    public static CheckResult manifestInvalid(Path manifestFile, String cause) {
        return new CheckResult(MANIFEST, List.of("invalid " + manifestFile + ": " + cause));
    }

    /**
     * @return a failed {@link CheckCategory#MANIFEST} result with the given message, e.g. a failed
     *         user-supplied check.
     */
    public static CheckResult manifestCheckFailed(String message) {
        return new CheckResult(MANIFEST, List.of(message));
    }

    /**
     * @return an {@link CheckCategory#INSTALLED_JARS} result with one issue per path the scan could not read;
     *         passed when the list is empty
     */
    public static CheckResult unreadablePaths(List<String> unreadable) {
        return new CheckResult(INSTALLED_JARS, unreadable.stream().map(path -> "cannot read " + path).toList());
    }

    /** @return a failed {@link CheckCategory#WILDFLY_ZIP} result: the archive is missing or could not be extracted. */
    public static CheckResult extractionFailed(Path zip, String cause) {
        return new CheckResult(WILDFLY_ZIP, List.of("cannot extract " + zip + ": " + cause));
    }

    /**
     * @return a failed {@link CheckCategory#INSTALLED_JARS} result: the installation tree could not be walked
     *         at all.
     */
    public static CheckResult scanFailed(Path root, String cause) {
        return new CheckResult(INSTALLED_JARS, List.of("cannot scan " + root + ": " + cause));
    }

    /**
     * @return a {@link CheckCategory#SBOM_VS_DISK} result with one issue per SBOM component that is missing
     *         on disk; passed when the list is empty
     */
    public static CheckResult sbomVsDisk(List<String> missing) {
        return new CheckResult(SBOM_VS_DISK, missing);
    }

    /**
     * @return a {@link CheckCategory#DISK_VS_SBOM} result with one issue per JAR on disk that the SBOM does
     *         not declare; passed when the list is empty
     */
    public static CheckResult diskVsSbom(List<String> undeclared) {
        return new CheckResult(DISK_VS_SBOM, undeclared);
    }

    /**
     * @return a {@link CheckCategory#SBOM_VS_MANIFEST} result with one issue per SBOM component the manifest
     *         lacks or lists with another version; passed when the list is empty
     */
    public static CheckResult sbomVsManifest(List<String> issues) {
        return new CheckResult(SBOM_VS_MANIFEST, issues);
    }

    /**
     * @return a failed {@link CheckCategory#SHADED} result with the given message, e.g. a failed
     *         user-supplied check.
     */
    public static CheckResult shadedCheckFailed(String message) {
        return new CheckResult(SHADED, List.of(message));
    }

    /**
     * @return a failed result of the given category: the check could not run because what it compares is
     *         unavailable. The cause is reported by the check of that input, not repeated here.
     */
    public static CheckResult unavailable(CheckCategory category, String input) {
        return new CheckResult(category, List.of("not checked: " + input + " is unavailable"));
    }
}
