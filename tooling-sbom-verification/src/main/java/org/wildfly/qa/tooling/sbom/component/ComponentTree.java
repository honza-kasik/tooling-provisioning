/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Traversal, selection and parent relationships in a CycloneDX component tree. */
public final class ComponentTree {

    private ComponentTree() {}

    /** Recursively flattens a component tree into verification views in SBOM order. */
    public static Stream<Component> all(List<org.cyclonedx.model.Component> components) {
        if (components == null) return Stream.empty();
        return components.stream().flatMap(c ->
                Stream.concat(Stream.of(new Component(c)), all(c.getComponents())));
    }

    /** Recursively collects the Maven components of the tree. */
    public static List<Component> mavenComponents(List<org.cyclonedx.model.Component> components) {
        return all(components)
                .filter(c -> c.purl() != null && c.purl().startsWith("pkg:maven/"))
                .toList();
    }

    /** Recursively collects the generic (non-Maven product file) components of the tree. */
    public static List<Component> genericComponents(List<org.cyclonedx.model.Component> components) {
        return all(components)
                .filter(c -> c.purl() != null && c.purl().startsWith("pkg:generic/"))
                .toList();
    }

    /** The normalized, root-relative occurrence paths of generic components in the tree. */
    public static Set<Path> genericLocations(List<org.cyclonedx.model.Component> components) {
        return genericComponents(components).stream()
                .flatMap(c -> c.occurrenceLocations().stream())
                .map(loc -> Path.of(loc).normalize())
                .collect(Collectors.toSet());
    }

    /**
     * Maps each component's purl to its immediate parent's purl. Top-level components and components
     * without a purl on either side of the relationship are not in the map.
     */
    public static Map<String, String> parentIndex(List<org.cyclonedx.model.Component> components) {
        Map<String, String> index = new LinkedHashMap<>();
        addParents(components, null, index);
        return index;
    }

    private static void addParents(List<org.cyclonedx.model.Component> components, String parentPurl,
                                   Map<String, String> index) {
        if (components == null) return;
        for (org.cyclonedx.model.Component c : components) {
            String purl = c.getPurl();
            if (purl != null && parentPurl != null) {
                index.put(purl, parentPurl);
            }
            addParents(c.getComponents(), purl, index);
        }
    }

    /** Recursively counts all components whose purl starts with the given scheme. */
    public static long countByPurlScheme(List<org.cyclonedx.model.Component> components, String scheme) {
        return all(components)
                .filter(c -> c.purl() != null && c.purl().startsWith(scheme))
                .count();
    }
}
