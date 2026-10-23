/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.component;

import org.cyclonedx.model.Evidence;
import org.cyclonedx.model.component.evidence.Occurrence;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComponentTreeTest {

    /**
     * Checks traversal and scheme selection through a container without a purl.
     *
     * <p>Passes when traversal preserves model order, Maven selection and count include both artifacts,
     * and generic selection retains the original model.</p>
     */
    @Test
    void nestedComponentsAreSelectedInSbomOrderWithoutLosingTheirModels() {
        org.cyclonedx.model.Component maven = component("pkg:maven/org.example/library@1");
        org.cyclonedx.model.Component generic = component("pkg:generic/client@1");
        org.cyclonedx.model.Component npm = component("pkg:npm/package@1");
        org.cyclonedx.model.Component container = component(null, maven, generic, npm);
        org.cyclonedx.model.Component otherMaven = component("pkg:maven/org.example/other@1");
        List<org.cyclonedx.model.Component> roots = List.of(container, otherMaven);

        assertEquals(List.of(container, maven, generic, npm, otherMaven),
                ComponentTree.all(roots).map(Component::model).toList());
        assertEquals(List.of(maven, otherMaven),
                ComponentTree.mavenComponents(roots).stream().map(Component::model).toList());
        assertSame(generic, ComponentTree.genericComponents(roots).get(0).model());
        assertEquals(2, ComponentTree.countByPurlScheme(roots, "pkg:maven/"));
    }

    /**
     * Checks nested generic occurrences with equivalent paths and blank or null locations.
     *
     * <p>Passes when generic paths are normalized and deduplicated, blank and null locations are ignored,
     * and occurrence lookup preserves the remaining locations in SBOM order.</p>
     */
    @Test
    void genericLocationsIncludeNestedComponentsAndNormalizeDuplicatePaths() {
        org.cyclonedx.model.Component generic = component("pkg:generic/client@1");
        Evidence evidence = new Evidence();
        evidence.setOccurrences(List.of(occurrence("bin/client.jar"), occurrence("./bin/client.jar"),
                occurrence("bin/other.jar"), occurrence(""), occurrence(null)));
        generic.setEvidence(evidence);
        org.cyclonedx.model.Component maven = component("pkg:maven/org.example/library@1", generic);

        assertEquals(Set.of(Path.of("bin/client.jar"), Path.of("bin/other.jar")),
                ComponentTree.genericLocations(List.of(maven)));
        assertEquals(List.of("bin/client.jar", "./bin/client.jar", "bin/other.jar"),
                ComponentTree.genericComponents(List.of(maven)).get(0).occurrenceLocations());
    }

    /**
     * Checks parent indexing across multiple levels with an unnamed component.
     *
     * <p>Passes when only the two child-to-immediate-parent purl relationships are included.</p>
     */
    @Test
    void parentIndexTracksImmediateParentsAndOmitsRootsAndMissingPurls() {
        org.cyclonedx.model.Component shaded = component("pkg:maven/org.example/shaded@1");
        org.cyclonedx.model.Component nested = component("pkg:maven/org.example/nested@1", shaded);
        org.cyclonedx.model.Component unnamed = component(null);
        org.cyclonedx.model.Component root = component("pkg:maven/org.example/root@1", nested, unnamed);

        assertEquals(Map.of(nested.getPurl(), root.getPurl(), shaded.getPurl(), nested.getPurl()),
                ComponentTree.parentIndex(List.of(root)));
    }

    /**
     * Checks tree operations when the SBOM component list is absent.
     *
     * <p>Passes when traversal and selections are empty, location and parent collections are empty,
     * and the scheme count is zero.</p>
     */
    @Test
    void absentComponentListsAreEmptyTrees() {
        assertEquals(List.of(), ComponentTree.all(null).toList());
        assertEquals(List.of(), ComponentTree.mavenComponents(null));
        assertEquals(List.of(), ComponentTree.genericComponents(null));
        assertEquals(Set.of(), ComponentTree.genericLocations(null));
        assertEquals(Map.of(), ComponentTree.parentIndex(null));
        assertEquals(0, ComponentTree.countByPurlScheme(null, "pkg:maven/"));
    }

    private static org.cyclonedx.model.Component component(String purl, org.cyclonedx.model.Component... children) {
        org.cyclonedx.model.Component component = new org.cyclonedx.model.Component();
        component.setPurl(purl);
        if (children.length > 0) {
            component.setComponents(List.of(children));
        }
        return component;
    }

    private static Occurrence occurrence(String location) {
        Occurrence occurrence = new Occurrence();
        occurrence.setLocation(location);
        return occurrence;
    }
}
