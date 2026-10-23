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
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentUtilsTest {

    private static final String JAR_PURL = "pkg:maven/org.jboss.modules/jboss-modules@2.1.6.Final";
    private static final String TXT_PURL =
            "pkg:maven/org.wildfly/wildfly-ee-feature-pack-product-conf@42.0.0.Beta1?classifier=version&type=txt";

    @Test
    void componentWithoutEvidenceIsAJar() {
        assertEquals(ComponentKind.JAR, ComponentUtils.componentKind(component(JAR_PURL, null)));
    }

    @Test
    void explicitJarTypeIsAJar() {
        assertEquals(ComponentKind.JAR,
                ComponentUtils.componentKind(component(JAR_PURL + "?type=jar", null)));
    }

    @Test
    void componentWithOnlyPomAnalysisEvidenceAndNoOccurrencesIsPomDerived() {
        assertEquals(ComponentKind.POM_DERIVED,
                ComponentUtils.componentKind(component(JAR_PURL, pomAnalysis(List.of()))));
    }

    @Test
    void componentWhoseOccurrencesAreAllLibDirectoriesIsANativeLib() {
        Evidence evidence = pomAnalysis(List.of(occurrence("modules/system/layers/base/org/foo/main/lib")));
        assertEquals(ComponentKind.NATIVE_LIB, ComponentUtils.componentKind(component(JAR_PURL, evidence)));
    }

    @Test
    void everyLibOccurrenceOfANativeLibIsReportedOnceInSbomOrder() {
        Evidence evidence = pomAnalysis(List.of(
                occurrence("modules/a/main/lib"), occurrence("modules/b/main/lib"), occurrence("modules/a/main/lib")));
        Component c = component(JAR_PURL, evidence);

        assertEquals(ComponentKind.NATIVE_LIB, ComponentUtils.componentKind(c));
        assertEquals(List.of("modules/a/main/lib", "modules/b/main/lib"), ComponentUtils.nativeLibDirs(c));
    }

    @Test
    void aComponentThatIsNotANativeLibHasNoNativeLibDirs() {
        assertEquals(List.of(), ComponentUtils.nativeLibDirs(component(JAR_PURL, null)));
    }

    @Test
    void aComponentWithALibAndAnotherOccurrenceIsAJarNotANativeLib() {
        Evidence evidence = pomAnalysis(List.of(occurrence("modules/a/main/lib"), occurrence("modules/a/main/a.jar")));
        assertEquals(ComponentKind.JAR, ComponentUtils.componentKind(component(JAR_PURL, evidence)));
    }

    @Test
    void genericComponentsAreCollectedWithTheirOccurrenceLocations() {
        Component generic = component("pkg:generic/jboss-client@1",
                pomAnalysis(List.of(occurrence("bin/client/jboss-client.jar"), occurrence("./bin/other.jar"))));
        Component maven = component(JAR_PURL, pomAnalysis(List.of(occurrence("jboss-modules.jar"))));

        assertEquals(List.of(generic), ComponentUtils.collectGenericComponents(List.of(maven, generic)));
        assertEquals(List.of("bin/client/jboss-client.jar", "./bin/other.jar"), ComponentUtils.occurrenceLocations(generic));
        assertEquals(java.util.Set.of(Path.of("bin/client/jboss-client.jar"), Path.of("bin/other.jar")),
                ComponentUtils.genericLocations(List.of(maven, generic)));
    }

    @Test
    void nonJarTypeIsAnOtherFile() {
        Evidence evidence = pomAnalysis(List.of(occurrence("version.txt")));
        Component c = component(TXT_PURL, evidence);

        assertEquals(ComponentKind.OTHER_FILE, ComponentUtils.componentKind(c));
        assertEquals(List.of("version.txt"), ComponentUtils.occurrenceLocations(c));
    }

    @Test
    void nonJarTypeWithoutOccurrenceIsStillAnOtherFileButHasNoLocation() {
        Component c = component(TXT_PURL, null);

        assertEquals(ComponentKind.OTHER_FILE, ComponentUtils.componentKind(c));
        assertEquals(List.of(), ComponentUtils.occurrenceLocations(c));
    }

    @Test
    void aPomDerivedComponentStaysPomDerivedWhateverItsType() {
        assertEquals(ComponentKind.POM_DERIVED,
                ComponentUtils.componentKind(component(TXT_PURL, pomAnalysis(List.of()))));
    }

    private static Component component(String purl, Evidence evidence) {
        Component c = new Component();
        c.setPurl(purl);
        c.setEvidence(evidence);
        return c;
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
