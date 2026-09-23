package org.wildfly.qa.tooling.pairwise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Generates test combinations of base layers with decorator layer subsets.
 *
 * <p>Produces combinations of decorator layers in sizes 2 and 3 (pairs and triples),
 * filters out combinations containing mutually exclusive layers, and pairs each with a
 * base layer (round-robin). This ensures all 2-way and 3-way decorator interactions
 * are tested.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * List<LayerCombination> combinations = PairwiseCombinationGenerator.builder()
 *         .baseLayers("ee-core-profile-server", "jaxrs-server")
 *         .decorators("ejb", "jpa", "jsf")
 *         .mutuallyExclusive("embedded-activemq", "remote-activemq")
 *         .build()
 *         .generate();
 * }</pre>
 */
public class PairwiseCombinationGenerator {

    private final List<String> baseLayers;
    private final List<String> decorators;
    private final List<String[]> exclusions;

    private PairwiseCombinationGenerator(Builder builder) {
        this.baseLayers = Collections.unmodifiableList(new ArrayList<>(builder.baseLayers));
        this.decorators = Collections.unmodifiableList(new ArrayList<>(builder.decorators));
        this.exclusions = Collections.unmodifiableList(new ArrayList<>(builder.exclusionPairs));
    }

    /** Creates a new builder for configuring the generator. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * System property to limit the number of generated combinations. When set, combinations
     * are subsampled proportionally from pairs and triples with even spacing.
     * When unset or {@code -1}, all combinations are generated.
     */
    public static final String MAX_COMBINATIONS_PROPERTY = "org.wildfly.qa.pairwise.maxCombinations";

    /**
     * Generates valid layer combinations: each decorator pair and triple combined with
     * a rotating base layer, excluding combinations that contain mutually exclusive layers.
     * If {@link #MAX_COMBINATIONS_PROPERTY} is set, subsamples proportionally.
     *
     * @return list of layer combinations
     */
    public List<LayerCombination> generate() {
        List<List<String>> pairs = new ArrayList<>();
        List<List<String>> triples = new ArrayList<>();

        for (int i = 0; i < decorators.size(); i++) {
            for (int j = i + 1; j < decorators.size(); j++) {
                List<String> pair = Arrays.asList(decorators.get(i), decorators.get(j));
                if (!containsExcludedPair(pair)) {
                    pairs.add(pair);
                }
            }
        }

        for (int i = 0; i < decorators.size(); i++) {
            for (int j = i + 1; j < decorators.size(); j++) {
                for (int k = j + 1; k < decorators.size(); k++) {
                    List<String> triple = Arrays.asList(
                            decorators.get(i), decorators.get(j), decorators.get(k));
                    if (!containsExcludedPair(triple)) {
                        triples.add(triple);
                    }
                }
            }
        }

        int max = Integer.getInteger(MAX_COMBINATIONS_PROPERTY, -1);
        if (max > 0 && max < pairs.size() + triples.size()) {
            int pairCount = Math.max(1, max * pairs.size() / (pairs.size() + triples.size()));
            int tripleCount = max - pairCount;
            pairs = evenSample(pairs, pairCount);
            triples = evenSample(triples, tripleCount);
        }

        List<List<String>> all = new ArrayList<>();
        all.addAll(pairs);
        all.addAll(triples);

        List<LayerCombination> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            String base = baseLayers.get(i % baseLayers.size());
            result.add(new LayerCombination(base, all.get(i)));
        }
        return result;
    }

    private <T> List<T> evenSample(List<T> list, int count) {
        if (count >= list.size()) {
            return list;
        }
        List<T> sampled = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sampled.add(list.get(i * list.size() / count));
        }
        return sampled;
    }

    private boolean containsExcludedPair(List<String> subset) {
        for (String[] excl : exclusions) {
            if (subset.contains(excl[0]) && subset.contains(excl[1])) {
                return true;
            }
        }
        return false;
    }

    /** A single test case: one base layer plus decorator layers. */
    public static class LayerCombination {

        private final String baseLayer;
        private final List<String> decorators;

        LayerCombination(String baseLayer, List<String> decorators) {
            this.baseLayer = baseLayer;
            this.decorators = Collections.unmodifiableList(new ArrayList<>(decorators));
        }

        public String getBaseLayer() { return baseLayer; }

        public List<String> getDecorators() { return decorators; }

        public List<String> getAllLayers() {
            List<String> all = new ArrayList<>();
            all.add(baseLayer);
            all.addAll(decorators);
            return all;
        }

        @Override
        public String toString() {
            return decorators.isEmpty() ? baseLayer : baseLayer + " + " + decorators;
        }
    }

    /** Fluent builder for {@link PairwiseCombinationGenerator}. */
    public static class Builder {

        private final List<String> baseLayers = new ArrayList<>();
        private final List<String> decorators = new ArrayList<>();
        private final List<String[]> exclusionPairs = new ArrayList<>();

        /** Adds base layers. One is selected per test case (round-robin). */
        public Builder baseLayers(String... layers) {
            baseLayers.addAll(Arrays.asList(layers));
            return this;
        }

        /** Adds decorator layers to combine in pairs and triples. */
        public Builder decorators(String... decorators) {
            this.decorators.addAll(Arrays.asList(decorators));
            return this;
        }

        /** Declares two decorator layers as mutually exclusive. */
        public Builder mutuallyExclusive(String layer1, String layer2) {
            exclusionPairs.add(new String[]{layer1, layer2});
            return this;
        }

        public PairwiseCombinationGenerator build() {
            return new PairwiseCombinationGenerator(this);
        }
    }
}
