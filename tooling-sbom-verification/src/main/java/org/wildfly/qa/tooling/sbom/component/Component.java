/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.component;

import org.cyclonedx.model.Evidence;
import org.cyclonedx.model.component.evidence.Identity;
import org.cyclonedx.model.component.evidence.Method;
import org.cyclonedx.model.component.evidence.Occurrence;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Installation-verification behavior of one CycloneDX component.
 *
 * <p>The original model is retained rather than copied, so all CycloneDX metadata remains available through
 * {@link #model()}. Changes to the model are reflected by this view.</p>
 *
 * @param model the parsed CycloneDX component
 */
public record Component(org.cyclonedx.model.Component model) {

    public Component {
        Objects.requireNonNull(model, "model");
    }

    public String purl() {
        return model.getPurl();
    }

    public String name() {
        return model.getName();
    }

    public String group() {
        return model.getGroup();
    }

    public String version() {
        return model.getVersion();
    }

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
    public ComponentKind kind() {
        Evidence ev = model.getEvidence();
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
        return hasNonJarType() ? ComponentKind.OTHER_FILE : ComponentKind.JAR;
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
    public List<String> nativeLibDirs() {
        Evidence ev = model.getEvidence();
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
    public String expectedJarFileName() {
        String versionSuffix = classifier()
                .filter(cl -> !cl.isBlank())
                .map(cl -> version() + "-" + cl)
                .orElse(version());
        return name() + "-" + versionSuffix + ".jar";
    }

    /**
     * Returns {@code true} when the purl declares a {@code type} qualifier that is not
     * {@code jar} (e.g. {@code type=txt}).
     */
    private boolean hasNonJarType() {
        String purl = purl();
        if (purl == null) return false;
        int q = purl.indexOf('?');
        if (q < 0) return false;
        return Arrays.stream(purl.substring(q + 1).split("&"))
                .anyMatch(p -> p.startsWith("type=") && !p.equals("type=jar"));
    }

    /** @return the location of every occurrence of the component, in the order of the SBOM; may be empty */
    public List<String> occurrenceLocations() {
        Evidence ev = model.getEvidence();
        if (ev == null || ev.getOccurrences() == null) return List.of();
        return ev.getOccurrences().stream()
                .map(Occurrence::getLocation)
                .filter(loc -> loc != null && !loc.isBlank())
                .toList();
    }

    /**
     * Extracts the {@code classifier} query parameter from a purl, or an empty
     * {@link Optional} if absent.
     */
    private Optional<String> classifier() {
        String purl = purl();
        if (purl == null) return Optional.empty();
        int q = purl.indexOf('?');
        if (q < 0) return Optional.empty();
        return Arrays.stream(purl.substring(q + 1).split("&"))
                .filter(p -> p.startsWith("classifier="))
                .map(p -> p.substring("classifier=".length()))
                .findFirst();
    }
}
