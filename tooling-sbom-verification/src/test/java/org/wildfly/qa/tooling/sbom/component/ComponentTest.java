/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.component;

import org.cyclonedx.model.Evidence;
import org.cyclonedx.model.component.evidence.Identity;
import org.cyclonedx.model.component.evidence.Method;
import org.cyclonedx.model.component.evidence.Occurrence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComponentTest {

    private static final String JAR_PURL = "pkg:maven/org.jboss.modules/jboss-modules@2.1.6.Final";
    private static final String TXT_PURL =
            "pkg:maven/org.wildfly/wildfly-ee-feature-pack-product-conf@42.0.0.Beta1?classifier=version&type=txt";

    /**
     * Checks classification when the component has no evidence.
     *
     * <p>Passes when the component is classified as JAR.</p>
     */
    @Test
    void componentWithoutEvidenceIsAJar() {
        assertEquals(ComponentKind.JAR, component(JAR_PURL, null).kind());
    }

    /**
     * Checks a purl that explicitly declares the JAR type.
     *
     * <p>Passes when the type=jar qualifier leaves the component classified as JAR.</p>
     */
    @Test
    void explicitJarTypeIsAJar() {
        assertEquals(ComponentKind.JAR,
                component(JAR_PURL + "?type=jar", null).kind());
    }

    /**
     * Checks a component identified only through POM analysis, with no installed occurrences.
     *
     * <p>Passes when the component is classified as POM_DERIVED.</p>
     */
    @Test
    void componentWithOnlyPomAnalysisEvidenceAndNoOccurrencesIsPomDerived() {
        assertEquals(ComponentKind.POM_DERIVED,
                component(JAR_PURL, pomAnalysis(List.of())).kind());
    }

    /**
     * Checks native library evidence whose occurrence ends in /lib.
     *
     * <p>Passes when the component is classified as NATIVE_LIB despite its POM-analysis evidence.</p>
     */
    @Test
    void componentWhoseOccurrencesAreAllLibDirectoriesIsANativeLib() {
        Evidence evidence = pomAnalysis(List.of(occurrence("modules/system/layers/base/org/foo/main/lib")));
        assertEquals(ComponentKind.NATIVE_LIB, component(JAR_PURL, evidence).kind());
    }

    /**
     * Checks native library directories with repeated occurrences.
     *
     * <p>Passes when classification is NATIVE_LIB and the directories are unique and retain SBOM order.</p>
     */
    @Test
    void everyLibOccurrenceOfANativeLibIsReportedOnceInSbomOrder() {
        Evidence evidence = pomAnalysis(List.of(
                occurrence("modules/a/main/lib"), occurrence("modules/b/main/lib"), occurrence("modules/a/main/lib")));
        Component c = component(JAR_PURL, evidence);

        assertEquals(ComponentKind.NATIVE_LIB, c.kind());
        assertEquals(List.of("modules/a/main/lib", "modules/b/main/lib"), c.nativeLibDirs());
    }

    /**
     * Checks directory lookup for a component without native library evidence.
     *
     * <p>Passes when the native library directory list is empty.</p>
     */
    @Test
    void aComponentThatIsNotANativeLibHasNoNativeLibDirs() {
        assertEquals(List.of(), component(JAR_PURL, null).nativeLibDirs());
    }

    /**
     * Checks a component with both a library directory and a JAR occurrence.
     *
     * <p>Passes when the component is classified as JAR rather than NATIVE_LIB.</p>
     */
    @Test
    void aComponentWithALibAndAnotherOccurrenceIsAJarNotANativeLib() {
        Evidence evidence = pomAnalysis(List.of(occurrence("modules/a/main/lib"), occurrence("modules/a/main/a.jar")));
        assertEquals(ComponentKind.JAR, component(JAR_PURL, evidence).kind());
    }

    /**
     * Checks a text-file component with an occurrence on disk.
     *
     * <p>Passes when classification is OTHER_FILE and the version.txt location is retained.</p>
     */
    @Test
    void nonJarTypeIsAnOtherFile() {
        Evidence evidence = pomAnalysis(List.of(occurrence("version.txt")));
        Component c = component(TXT_PURL, evidence);

        assertEquals(ComponentKind.OTHER_FILE, c.kind());
        assertEquals(List.of("version.txt"), c.occurrenceLocations());
    }

    /**
     * Checks a text-file component without occurrence evidence.
     *
     * <p>Passes when classification is OTHER_FILE and the occurrence location list is empty.</p>
     */
    @Test
    void nonJarTypeWithoutOccurrenceIsStillAnOtherFileButHasNoLocation() {
        Component c = component(TXT_PURL, null);

        assertEquals(ComponentKind.OTHER_FILE, c.kind());
        assertEquals(List.of(), c.occurrenceLocations());
    }

    /**
     * Checks precedence of POM-only evidence over a non-JAR type qualifier.
     *
     * <p>Passes when the text-file component is classified as POM_DERIVED.</p>
     */
    @Test
    void aPomDerivedComponentStaysPomDerivedWhateverItsType() {
        assertEquals(ComponentKind.POM_DERIVED,
                component(TXT_PURL, pomAnalysis(List.of())).kind());
    }

    /**
     * Checks that the verification view retains the original CycloneDX model.
     *
     * <p>Passes when model identity and description are preserved and later coordinate changes are visible.</p>
     */
    @Test
    void viewRetainsTheOriginalModelAndReflectsMetadataChanges() {
        org.cyclonedx.model.Component model = new org.cyclonedx.model.Component();
        model.setDescription("metadata retained from CycloneDX");
        Component component = new Component(model);

        assertSame(model, component.model());
        model.setName("library");
        model.setGroup("org.example");
        model.setVersion("1");
        model.setPurl("pkg:maven/org.example/library@1");
        assertEquals("library", component.name());
        assertEquals("org.example", component.group());
        assertEquals("1", component.version());
        assertEquals("pkg:maven/org.example/library@1", component.purl());
        assertEquals("metadata retained from CycloneDX", component.model().getDescription());
    }

    /**
     * Checks filename derivation for a JAR with a tests classifier.
     *
     * <p>Passes when the filename is jboss-modules-2.1.6.Final-tests.jar.</p>
     */
    @Test
    void expectedJarFileNameIncludesTheClassifier() {
        Component component = component(JAR_PURL + "?type=jar&classifier=tests", null);
        component.model().setName("jboss-modules");
        component.model().setVersion("2.1.6.Final");

        assertEquals("jboss-modules-2.1.6.Final-tests.jar", component.expectedJarFileName());
    }

    /**
     * Checks filename derivation with absent purls, absent qualifiers and empty classifiers.
     *
     * <p>Passes when every case produces jboss-modules-2.1.6.Final.jar without an extra classifier suffix.</p>
     */
    @Test
    void expectedJarFileNameOmitsAbsentOrBlankClassifiers() {
        for (String purl : new String[] {null, JAR_PURL, JAR_PURL + "?type=jar", JAR_PURL + "?classifier="}) {
            Component component = component(purl, null);
            component.model().setName("jboss-modules");
            component.model().setVersion("2.1.6.Final");

            assertEquals("jboss-modules-2.1.6.Final.jar", component.expectedJarFileName());
        }
    }

    private static Component component(String purl, Evidence evidence) {
        org.cyclonedx.model.Component c = new org.cyclonedx.model.Component();
        c.setPurl(purl);
        c.setEvidence(evidence);
        return new Component(c);
    }

    private static Occurrence occurrence(String location) {
        Occurrence o = new Occurrence();
        o.setLocation(location);
        return o;
    }

    /** Evidence like the SBOM generator writes it: the identity comes from the POM only. */
    private static Evidence pomAnalysis(List<Occurrence> occurrences) {
        Method method = new Method();
        method.setTechnique(Method.Technique.MANIFEST_ANALYSIS);
        method.setValue("maven-pom-analysis");
        Identity identity = new Identity();
        identity.setMethods(List.of(method));
        Evidence evidence = new Evidence();
        evidence.setIdentities(List.of(identity));
        evidence.setOccurrences(occurrences);
        return evidence;
    }
}
