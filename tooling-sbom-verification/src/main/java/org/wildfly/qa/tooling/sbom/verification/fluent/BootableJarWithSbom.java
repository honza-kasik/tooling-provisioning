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
import java.util.List;
import java.util.function.Predicate;

/**
 * A bootable JAR that is expected to have a valid SBOM in {@code META-INF/sbom}. Further conditions on it
 * can be added with {@link #withBomCheck(Predicate, String)}; the chain continues with
 * {@link #withExtractedWildflyZipSbomVerifier()}.
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

    /** Continues with the WildFly installation in {@code wildfly.zip}, extracted when {@code verify()} runs. */
    public SbomVerifier withExtractedWildflyZipSbomVerifier() {
        return BootableJarSbomVerifier.extractedWildfly(wildflyZip, sbom.stack());
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
