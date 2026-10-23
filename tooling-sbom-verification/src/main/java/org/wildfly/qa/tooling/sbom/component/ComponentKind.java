/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.component;

/**
 * Classification of a CycloneDX SBOM component for installation verification purposes.
 */
public enum ComponentKind {
    /** Sub-artifact declared in a parent POM; never installed as a standalone file. */
    POM_DERIVED,
    /** Installed as a native {@code .so}/{@code .dll} inside a module {@code lib/} directory. */
    NATIVE_LIB,
    /**
     * A Maven artifact that is not a JAR ({@code type=} other than {@code jar} in the purl), installed as a
     * plain file, for example the {@code version.txt} of the feature pack ({@code type=txt}).
     */
    OTHER_FILE,
    /** A JAR installed on disk. */
    JAR
}
