/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.result;

/**
 * What a {@link CheckResult} is about. The set is closed: the checks that take a user-supplied condition
 * report under the category of what they look at ({@link #SBOM}, {@link #MANIFEST}, {@link #SHADED}).
 * {@link #toString()} is the short, stable label used in reports.
 */
public enum CheckCategory {

    /** Whether the installation has a valid SBOM, and whether that is what was expected. */
    SBOM("SBOM"),

    /** Whether the installation has a valid manifest, and whether that is what was expected. */
    MANIFEST("Manifest"),

    /** Whether every path of the installation could be read when looking for JARs. */
    INSTALLED_JARS("Installed JARs"),

    /** Whether the {@code wildfly.zip} of a bootable JAR can be extracted. */
    WILDFLY_ZIP("wildfly.zip"),

    /** Whether every component declared in the SBOM is present on disk. */
    SBOM_VS_DISK("SBOM vs disk"),

    /** Whether every JAR on disk is declared in the SBOM. */
    DISK_VS_SBOM("Disk vs SBOM"),

    /** Whether every installed component of the SBOM is in the manifest with the same version. */
    SBOM_VS_MANIFEST("SBOM vs manifest"),

    /** Whether the shaded (POM-derived) artifacts of the SBOM meet a user-supplied expectation. */
    SHADED("Shaded artifacts");

    private final String label;

    CheckCategory(String label) {
        this.label = label;
    }

    /** @return the short label of the category, e.g. {@code "SBOM vs disk"} */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
