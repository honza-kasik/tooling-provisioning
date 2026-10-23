/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.component;

import org.cyclonedx.model.Component;
import org.cyclonedx.model.Evidence;
import org.cyclonedx.model.component.evidence.Identity;
import org.cyclonedx.model.component.evidence.Method;
import org.cyclonedx.model.component.evidence.Occurrence;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility methods for inspecting and classifying CycloneDX {@link Component} objects.
 */
public final class ComponentUtils {

    private ComponentUtils() {}

    /**
     * Classifies a component for installation verification purposes.
     *
     * <ul>
     *   <li>{@link ComponentKind#POM_DERIVED} — only {@code maven-pom-analysis} evidence and no
     *       occurrences; never installed as a standalone file.</li>
     *   <li>{@link ComponentKind#NATIVE_LIB} — all occurrences end with {@code /lib}; installed
     *       as a shared library.</li>
     *   <li>{@link ComponentKind#OTHER_FILE} — the purl declares a {@code type} other than {@code jar}
     *       (e.g. {@code type=txt}); installed as a plain file.</li>
     *   <li>{@link ComponentKind#JAR} — everything else.</li>
     * </ul>
     */
    public static ComponentKind componentKind(Component c) {
        Evidence ev = c.getEvidence();
        if (ev != null) {
            List<Occurrence> occurrences = ev.getOccurrences();
            if (occurrences != null && !occurrences.isEmpty()) {
                boolean allLib = occurrences.stream()
                        .allMatch(o -> o.getLocation() != null && o.getLocation().endsWith("/lib"));
                if (allLib) return ComponentKind.NATIVE_LIB;
            } else if (isPomDerived(ev)) {
                return ComponentKind.POM_DERIVED;
            }
        }
        return hasNonJarType(c) ? ComponentKind.OTHER_FILE : ComponentKind.JAR;
    }

    private static boolean isPomDerived(Evidence ev) {
        List<Identity> identities = ev.getIdentities();
        if (identities == null || identities.isEmpty()) return false;
        return identities.stream().allMatch(id -> {
            List<Method> methods = id.getMethods();
            if (methods == null || methods.isEmpty()) return false;
            return methods.stream().allMatch(m ->
                    m.getTechnique() == Method.Technique.MANIFEST_ANALYSIS
                            && "maven-pom-analysis".equals(m.getValue()));
        });
    }

    /**
     * Returns the relative paths of the native lib directories of a {@link ComponentKind#NATIVE_LIB}
     * component: the location of every occurrence that ends with {@code /lib}, without duplicates and in
     * the order of the SBOM. The directory is where the installation keeps the platform-specific native
     * libraries of the component, for example {@code modules/.../openssl/main/lib}.
     */
    public static List<String> nativeLibDirs(Component c) {
        Evidence ev = c.getEvidence();
        if (ev == null || ev.getOccurrences() == null) return List.of();
        return ev.getOccurrences().stream()
                .map(Occurrence::getLocation)
                .filter(loc -> loc != null && loc.endsWith("/lib"))
                .distinct()
                .toList();
    }

    /**
     * Builds the expected on-disk JAR filename from a component's SBOM metadata:
     * {@code <name>-<version>[-<classifier>].jar}.
     */
    public static String expectedJarFileName(Component c) {
        String versionSuffix = purlClassifier(c.getPurl())
                .filter(cl -> !cl.isBlank())
                .map(cl -> c.getVersion() + "-" + cl)
                .orElse(c.getVersion());
        return c.getName() + "-" + versionSuffix + ".jar";
    }

    /**
     * Returns {@code true} when the purl declares a {@code type} qualifier that is not
     * {@code jar} (e.g. {@code type=txt}).
     */
    private static boolean hasNonJarType(Component c) {
        String purl = c.getPurl();
        if (purl == null) return false;
        int q = purl.indexOf('?');
        if (q < 0) return false;
        return Arrays.stream(purl.substring(q + 1).split("&"))
                .anyMatch(p -> p.startsWith("type=") && !p.equals("type=jar"));
    }

    /** Recursively flattens a component tree into a single stream of all components. */
    public static Stream<Component> collectAllComponents(List<Component> components) {
        if (components == null) return Stream.empty();
        return components.stream()
                .flatMap(c -> Stream.concat(Stream.of(c), collectAllComponents(c.getComponents())));
    }

    /**
     * The locations of the {@code pkg:generic} components of the tree: the normalized, root-relative path of
     * every occurrence. A generic component is a file of the product that is not a Maven artifact, such as an
     * assembled fat JAR.
     */
    public static Set<Path> genericLocations(List<Component> components) {
        return collectGenericComponents(components).stream()
                .flatMap(c -> occurrenceLocations(c).stream())
                .map(loc -> Path.of(loc).normalize())
                .collect(Collectors.toSet());
    }

    /** Recursively collects the {@code pkg:generic} components from the SBOM component tree. */
    public static List<Component> collectGenericComponents(List<Component> components) {
        return collectAllComponents(components)
                .filter(c -> c.getPurl() != null && c.getPurl().startsWith("pkg:generic/"))
                .toList();
    }

    /** @return the location of every occurrence of the component, in the order of the SBOM; may be empty */
    public static List<String> occurrenceLocations(Component c) {
        Evidence ev = c.getEvidence();
        if (ev == null || ev.getOccurrences() == null) return List.of();
        return ev.getOccurrences().stream()
                .map(Occurrence::getLocation)
                .filter(loc -> loc != null && !loc.isBlank())
                .toList();
    }

    /** Recursively collects all maven-coordinate components from the SBOM component tree. */
    public static List<Component> collectMavenComponents(List<Component> components) {
        if (components == null) return Collections.emptyList();
        return collectAllComponents(components)
                .filter(c -> c.getPurl() != null && c.getPurl().startsWith("pkg:maven/"))
                .collect(Collectors.toList());
    }

    /**
     * Maps the purl of every component in the tree to the purl of its nearest parent. Top-level components
     * have no parent and are not in the map; neither are components without a purl.
     */
    public static Map<String, String> parentIndex(List<Component> components) {
        Map<String, String> index = new LinkedHashMap<>();
        addParents(components, null, index);
        return index;
    }

    private static void addParents(List<Component> components, String parentPurl, Map<String, String> index) {
        if (components == null) return;
        for (Component c : components) {
            String purl = c.getPurl();
            if (purl != null && parentPurl != null) {
                index.put(purl, parentPurl);
            }
            addParents(c.getComponents(), purl, index);
        }
    }

    /** Recursively counts all components whose purl starts with the given scheme. */
    public static long countByPurlScheme(List<Component> components, String scheme) {
        if (components == null) return 0;
        return collectAllComponents(components)
                .filter(c -> c.getPurl() != null && c.getPurl().startsWith(scheme))
                .count();
    }

    /**
     * Extracts the {@code classifier} query parameter from a purl, or an empty
     * {@link Optional} if absent.
     */
    public static Optional<String> purlClassifier(String purl) {
        if (purl == null) return Optional.empty();
        int q = purl.indexOf('?');
        if (q < 0) return Optional.empty();
        return Arrays.stream(purl.substring(q + 1).split("&"))
                .filter(p -> p.startsWith("classifier="))
                .map(p -> p.substring("classifier=".length()))
                .findFirst();
    }
}
