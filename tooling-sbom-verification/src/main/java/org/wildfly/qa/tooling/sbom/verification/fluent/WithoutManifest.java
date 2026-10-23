/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification.fluent;

import org.wildfly.qa.tooling.sbom.result.CheckCategory;
import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.verification.Context;
import org.wildfly.qa.tooling.sbom.result.CheckResults;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * An installation that is expected to have no {@code .installation/manifest.yaml}. The check is implicit:
 * it is added to the stack when this stage switches to the next one or verifies.
 */
public final class WithoutManifest {

    private final Context context;
    private final List<Supplier<CheckResult>> checks;

    WithoutManifest(Context context, List<Supplier<CheckResult>> parentChecks) {
        this.context = context;
        this.checks = new ArrayList<>(parentChecks);
    }

    /** Compares the SBOM with the JARs on disk. */
    public WithDiskChecks withDiskChecks() {
        return new WithDiskChecks(context, stack());
    }

    /**
     * Runs the checks of the parents and of this stage.
     *
     * @return one result per check, in the order they ran; never {@code null}
     */
    public List<CheckResult> verify() {
        return context.run(stack());
    }

    /** @return the checks collected so far followed by the implicit check of this stage; a new list */
    private List<Supplier<CheckResult>> stack() {
        List<Supplier<CheckResult>> stack = new ArrayList<>(checks);
        stack.add(this::checkManifestAbsent);
        return stack;
    }

    /** Fails if the installation has a manifest. */
    private CheckResult checkManifestAbsent() {
        Path file = context.root().resolve(".installation").resolve("manifest.yaml");
        return Files.exists(file)
                ? CheckResults.manifestUnexpected(file)
                : CheckResults.passed(CheckCategory.MANIFEST);
    }
}
