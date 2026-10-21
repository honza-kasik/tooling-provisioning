/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification.fluent;

import org.wildfly.qa.tooling.sbom.result.CheckCategory;
import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.verification.Context;
import org.wildfly.qa.tooling.sbom.result.CheckResults;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * An installation that is expected to have no SBOM. The check is implicit: it is added to the stack when
 * {@link #verify()} runs.
 */
public final class WithoutSbom {

    private final Context context;
    private final List<Supplier<CheckResult>> checks;

    public WithoutSbom(Context context, List<Supplier<CheckResult>> parentChecks) {
        this.context = context;
        this.checks = new ArrayList<>(parentChecks);
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
    public List<Supplier<CheckResult>> stack() {
        List<Supplier<CheckResult>> stack = new ArrayList<>(checks);
        stack.add(this::checkSbomAbsent);
        return stack;
    }

    /** Fails if the installation has an SBOM. */
    private CheckResult checkSbomAbsent() {
        return context.sbomFile()
                .map(CheckResults::sbomUnexpected)
                .orElseGet(() -> CheckResults.passed(CheckCategory.SBOM));
    }
}
