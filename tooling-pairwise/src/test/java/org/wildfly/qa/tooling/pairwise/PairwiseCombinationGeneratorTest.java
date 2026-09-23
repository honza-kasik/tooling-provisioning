package org.wildfly.qa.tooling.pairwise;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairwiseCombinationGeneratorTest {

    @Test
    void generatesAllPairsAndTriples() {
        List<PairwiseCombinationGenerator.LayerCombination> result = PairwiseCombinationGenerator.builder()
                .baseLayers("base1")
                .decorators("a", "b", "c")
                .build()
                .generate();

        // 3 decorators: C(3,2)=3 pairs + C(3,3)=1 triple = 4
        assertEquals(4, result.size());
    }

    @Test
    void rotatesBaseLayers() {
        List<PairwiseCombinationGenerator.LayerCombination> result = PairwiseCombinationGenerator.builder()
                .baseLayers("base1", "base2")
                .decorators("a", "b", "c", "d")
                .build()
                .generate();

        long base1Count = result.stream().filter(c -> c.getBaseLayer().equals("base1")).count();
        long base2Count = result.stream().filter(c -> c.getBaseLayer().equals("base2")).count();
        assertTrue(Math.abs(base1Count - base2Count) <= 1, "Base layers should be evenly distributed");
    }

    @Test
    void excludesMutuallyExclusivePairs() {
        List<PairwiseCombinationGenerator.LayerCombination> result = PairwiseCombinationGenerator.builder()
                .baseLayers("base1")
                .decorators("a", "b", "c")
                .mutuallyExclusive("a", "b")
                .build()
                .generate();

        for (PairwiseCombinationGenerator.LayerCombination combo : result) {
            List<String> decs = combo.getDecorators();
            assertFalse(decs.contains("a") && decs.contains("b"),
                    "Should not contain mutually exclusive pair: " + combo);
        }
    }

    @Test
    void getAllLayersIncludesBase() {
        PairwiseCombinationGenerator.LayerCombination combo = PairwiseCombinationGenerator.builder()
                .baseLayers("base1")
                .decorators("a", "b")
                .build()
                .generate()
                .get(0);

        List<String> all = combo.getAllLayers();
        assertEquals("base1", all.get(0));
        assertTrue(all.containsAll(combo.getDecorators()));
        assertEquals(combo.getDecorators().size() + 1, all.size());
    }

    @Test
    void coversAllTwoWayInteractions() {
        List<String> decorators = List.of("a", "b", "c", "d");

        List<PairwiseCombinationGenerator.LayerCombination> result = PairwiseCombinationGenerator.builder()
                .baseLayers("base1")
                .decorators(decorators.toArray(String[]::new))
                .build()
                .generate();

        Set<String> coveredPairs = new HashSet<>();
        for (PairwiseCombinationGenerator.LayerCombination combo : result) {
            List<String> decs = combo.getDecorators();
            for (int i = 0; i < decs.size(); i++) {
                for (int j = i + 1; j < decs.size(); j++) {
                    coveredPairs.add(decs.get(i) + "+" + decs.get(j));
                }
            }
        }

        // all C(4,2)=6 pairs should be covered
        for (int i = 0; i < decorators.size(); i++) {
            for (int j = i + 1; j < decorators.size(); j++) {
                assertTrue(coveredPairs.contains(decorators.get(i) + "+" + decorators.get(j)),
                        "Missing pair: " + decorators.get(i) + "+" + decorators.get(j));
            }
        }
    }

    @Test
    void maxCombinationsLimitsOutput() {
        System.setProperty(PairwiseCombinationGenerator.MAX_COMBINATIONS_PROPERTY, "3");
        try {
            List<PairwiseCombinationGenerator.LayerCombination> result = PairwiseCombinationGenerator.builder()
                    .baseLayers("base1")
                    .decorators("a", "b", "c", "d", "e")
                    .build()
                    .generate();

            assertEquals(3, result.size());
        } finally {
            System.clearProperty(PairwiseCombinationGenerator.MAX_COMBINATIONS_PROPERTY);
        }
    }
}
