package org.wildfly.qa.tooling.strings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pairs of pattern-replacement to be used in replacing substrings in strings
 */
public class ReplacementPairs {

    private final Map<String, String> pairs = new LinkedHashMap<>();

    public ReplacementPairs addPair(String pattern, String replacement) {
        this.pairs.put(Pattern.quote(pattern), replacement);
        return this;
    }

    public Map<String, String> getPairs() {
        return pairs;
    }

}
