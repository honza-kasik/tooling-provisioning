/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification.fluent;

import org.cyclonedx.model.Bom;
import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.verification.BootableJarSbomVerifier;
import org.wildfly.qa.tooling.sbom.verification.SbomVerifier;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * A bootable JAR that is expected to have a valid SBOM in {@code META-INF/sbom}. Further conditions on it
 * can be added with {@link #withBomCheck(Predicate, String)}; the chain continues with
 * {@link #withExtractedWildflyZipSbomVerifier(Path, Collection)}.
 */
public final class BootableJarWithSbom {

    private final WithSbom sbom;
    private final Path wildflyZip;

    public BootableJarWithSbom(WithSbom sbom, Path wildflyZip) {
        this.sbom = sbom;
        this.wildflyZip = wildflyZip;
    }

    /**
     * Adds a condition on the parsed SBOM of the bootable JAR; see {@link WithSbom#withBomCheck}.
     *
     * @return this stage
     */
    public BootableJarWithSbom withBomCheck(Predicate<Bom> predicate, String errorMessage) {
        sbom.withBomCheck(predicate, errorMessage);
        return this;
    }

    /**
     * Also requires the SBOM of the bootable JAR to conform to the CycloneDX schema; see
     * {@link WithSbom#withSchemaValidation()}. Off by default. It does not apply to the SBOM of the
     * extracted WildFly; ask for it on that stage.
     *
     * @return this stage
     */
    public BootableJarWithSbom withSchemaValidation() {
        sbom.withSchemaValidation();
        return this;
    }

    /**
     * Continues with the WildFly installation in {@code wildfly.zip}, extracted when {@code verify()} runs.
     *
     * @param sbomFile          where the SBOM is in the extracted installation, relative to its root; see
     *                          {@link SbomVerifier#SbomVerifier(Path, Path, Collection)}
     * @param excludedScanPaths the directories, relative to the extracted installation, that the scan for
     *                          installed JARs skips; the complete list, see
     *                          {@link SbomVerifier#SbomVerifier(Path, Path, Collection)}
     */
    public SbomVerifier withExtractedWildflyZipSbomVerifier(Path sbomFile, Collection<Path> excludedScanPaths) {
        return BootableJarSbomVerifier.extractedWildfly(wildflyZip, sbom.stack(), sbomFile, excludedScanPaths);
    }

    /**
     * Runs the checks of the bootable JAR only; {@code wildfly.zip} is not looked at.
     *
     * @return one result per check, in the order they ran; never {@code null}
     */
    public List<CheckResult> verify() {
        return sbom.verify();
    }
}
