/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification;

import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.result.CheckResults;
import org.wildfly.qa.tooling.sbom.verification.fluent.WithSbom;
import org.wildfly.qa.tooling.sbom.verification.fluent.WithoutSbom;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Entry point of the fluent verification of the WildFly installation at a given root. The first call states
 * whether the installation is expected to have an SBOM:
 * <pre>
 *     new SbomVerifier(root).withSbom().withManifest().withDiskChecks().verify();
 *     new SbomVerifier(root).withoutSbom().verify();
 * </pre>
 *
 * <p>The verifier holds the base of the check stack: the installed-JAR check, which does not depend on an
 * SBOM or a manifest. Each stage passes its stack, extended by its own check, to the next stage when it
 * switches to it. The stage constructors are package-private, so a stage can only be reached through this
 * chain. Nothing is read until {@code verify()} is called.</p>
 */
public final class SbomVerifier {

    private final Context context;
    private final List<Supplier<CheckResult>> parentChecks;

    /**
     * @param root the installation root (the directory that contains {@code jboss-modules.jar},
     *             {@code modules/}, {@code bin/}, etc.)
     */
    public SbomVerifier(Path root) {
        this(new Context(root), List.of());
    }

    /** A verifier that continues another chain: {@code parentChecks} run before its own. */
    public SbomVerifier(Context context, List<Supplier<CheckResult>> parentChecks) {
        this.context = context;
        this.parentChecks = List.copyOf(parentChecks);
    }

    /** Expects the installation to have an SBOM. */
    public WithSbom withSbom() {
        return new WithSbom(context, baseChecks());
    }

    /** Expects the installation to have no SBOM. */
    public WithoutSbom withoutSbom() {
        return new WithoutSbom(context, baseChecks());
    }

    /** @return the parent checks followed by the installed-JAR check; a new list */
    private List<Supplier<CheckResult>> baseChecks() {
        List<Supplier<CheckResult>> checks = new ArrayList<>(parentChecks);
        checks.add(this::checkInstalledJars);
        return checks;
    }

    /**
     * Reports every path of the installation that could not be read when looking for JARs, or that the
     * tree could not be walked at all.
     */
    private CheckResult checkInstalledJars() {
        return context.scan().get()
                .map(scan -> CheckResults.unreadablePaths(scan.unreadable()))
                .orElseGet(() -> CheckResults.scanFailed(context.root(), context.scan().error().orElseThrow()));
    }
}
