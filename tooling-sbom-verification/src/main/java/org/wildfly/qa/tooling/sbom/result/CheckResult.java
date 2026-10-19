/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.result;

import java.util.List;
import java.util.Objects;

/**
 * The result of a single SBOM check: the category of what was checked and a (possibly empty) list of issue
 * strings. An empty list means the check passed. Use {@link CheckResults} to create the known results.
 *
 * @param category short, stable label of the checked aspect, e.g. {@link CheckResults#SBOM}
 * @param issues   what was found wrong; empty when the check passed
 */
public record CheckResult(String category, List<String> issues) {

    public CheckResult {
        Objects.requireNonNull(category, "category");
        issues = List.copyOf(issues);
    }

    /** @return {@code true} when no issues were found. */
    public boolean passed() { return issues.isEmpty(); }
}
