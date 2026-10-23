/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification.fluent;

import org.wildfly.qa.tooling.sbom.result.CheckResult;

import java.util.List;

/**
 * A bootable JAR that is expected to have no SBOM in {@code META-INF/sbom}. The chain ends here: without an
 * SBOM there is nothing to verify the packaged {@code wildfly.zip} against, so it is not offered. Together
 * with the base check of {@link BootableJarSbomVerifier}, this verifies that neither an SBOM nor a manifest
 * is present.
 */
public final class BootableJarWithoutSbom {

    private final WithoutSbom noSbom;

    public BootableJarWithoutSbom(WithoutSbom noSbom) {
        this.noSbom = noSbom;
    }

    /**
     * Runs the checks of the bootable JAR; {@code wildfly.zip} is not looked at.
     *
     * @return one result per check, in the order they ran; never {@code null}
     */
    public List<CheckResult> verify() {
        return noSbom.verify();
    }
}
