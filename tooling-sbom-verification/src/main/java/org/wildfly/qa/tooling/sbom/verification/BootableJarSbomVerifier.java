/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification;

import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.result.CheckResults;
import org.wildfly.qa.tooling.sbom.verification.fluent.BootableJarWithSbom;
import org.wildfly.qa.tooling.sbom.verification.fluent.BootableJarWithoutSbom;
import org.wildfly.qa.tooling.sbom.verification.fluent.WithSbom;
import org.wildfly.qa.tooling.sbom.verification.fluent.WithoutSbom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Entry point of the fluent verification of an extracted bootable JAR. A bootable JAR has two levels: its
 * own SBOM in {@code META-INF/sbom/}, and the WildFly installation packaged in {@code wildfly.zip}. The
 * outer level is stated first, then the chain continues with a verifier of the extracted installation:
 * <pre>
 *     new BootableJarSbomVerifier(root).withSbom().withBomCheck(...)
 *             .withExtractedWildflyZipSbomVerifier().withSbom().withManifest().withDiskChecks().verify();
 *     new BootableJarSbomVerifier(root).withoutSbom().verify();
 * </pre>
 *
 * <p>Only a bootable JAR with an SBOM can continue to the extracted installation: without one there is
 * nothing to verify {@code wildfly.zip} against, so {@code withoutSbom()} ends the chain.</p>
 *
 * <p>The outer root is {@code META-INF/sbom} of the bootable JAR. It is not an installation: the base check
 * of this verifier is that it has no {@code .installation/manifest.yaml}. {@code wildfly.zip} is extracted to
 * a temporary directory when {@code verify()} runs, and the directory is deleted afterwards. Nothing is
 * read before that.</p>
 */
public final class BootableJarSbomVerifier {

    private final Context context;
    private final Path wildflyZip;

    /**
     * @param bootableRoot the extracted bootable JAR (the directory that contains {@code META-INF/} and
     *                     {@code wildfly.zip})
     */
    public BootableJarSbomVerifier(Path bootableRoot) {
        Objects.requireNonNull(bootableRoot, "bootableRoot");
        this.context = new Context(bootableRoot.resolve("META-INF").resolve("sbom"));
        this.wildflyZip = bootableRoot.resolve("wildfly.zip");
    }

    /** Expects the bootable JAR to have an SBOM. */
    public BootableJarWithSbom withSbom() {
        return new BootableJarWithSbom(new WithSbom(context, baseChecks()), wildflyZip);
    }

    /** Expects the bootable JAR to have no SBOM (and no manifest). The chain ends there. */
    public BootableJarWithoutSbom withoutSbom() {
        return new BootableJarWithoutSbom(new WithoutSbom(context, baseChecks()));
    }

    private List<Supplier<CheckResult>> baseChecks() {
        List<Supplier<CheckResult>> checks = new ArrayList<>();
        checks.add(this::checkNoManifest);
        return checks;
    }

    /** Fails if the SBOM directory of the bootable JAR has a manifest. */
    private CheckResult checkNoManifest() {
        Path file = context.root().resolve(".installation").resolve("manifest.yaml");
        return Files.exists(file)
                ? CheckResults.manifestUnexpected(file)
                : CheckResults.passed(CheckResults.MANIFEST);
    }

    /**
     * Continues the chain with a verifier of the WildFly installation in {@code wildfly.zip}. The
     * {@code outerChecks} run first, then the extraction, then whatever the returned verifier adds.
     */
    public static SbomVerifier extractedWildfly(Path wildflyZip, List<Supplier<CheckResult>> outerChecks) {
        Context inner = Context.extractedFrom(wildflyZip);
        List<Supplier<CheckResult>> checks = new ArrayList<>(outerChecks);
        checks.add(() -> checkExtracted(wildflyZip, inner));
        return new SbomVerifier(inner, checks);
    }

    /** Extracts {@code wildfly.zip}; fails if it is missing or cannot be extracted. */
    private static CheckResult checkExtracted(Path wildflyZip, Context inner) {
        return inner.rootError()
                .map(error -> CheckResults.extractionFailed(wildflyZip, error))
                .orElseGet(() -> CheckResults.passed(CheckResults.WILDFLY_ZIP));
    }
}
