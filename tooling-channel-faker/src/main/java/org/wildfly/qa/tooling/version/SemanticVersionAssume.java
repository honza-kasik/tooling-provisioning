package org.wildfly.qa.tooling.version;

import java.util.Arrays;
import java.util.List;

/**
 * Simple version assume implementation covering the most basic case of iterating and reducing micro version number
 * for semantic versioning use.
 */
public class SemanticVersionAssume implements VersionAssume {

    private final List<String> currentVersionTokenized;

    /**
     * Create an instance of SemanticVersionAssume
     * @param currentVersion current version
     */
    public SemanticVersionAssume(String currentVersion) {
        this.currentVersionTokenized = Arrays.asList(currentVersion.split("\\."));
    }

    /**
     * Find next version
     * @return next version
     */
    @Override
    public String nextVersion() {
        final List<String> tokens = this.currentVersionTokenized;
        final List<String> appendix = tokens.subList(3, tokens.size());

        final String versionWithoutAppendix = String.join(".", tokens.subList(0, 2)) + "." +
                (Integer.parseInt(tokens.get(2)) + 1);

        return appendix.isEmpty() ?
                versionWithoutAppendix :
                versionWithoutAppendix  + "." + String.join(".", tokens.subList(3, tokens.size()));
    }

    /**
     * Find previous version
     * @return previous version
     */
    @Override
    public String previousVersion() {
        final List<String> tokens = this.currentVersionTokenized;
        final int microVersion = Integer.parseInt(tokens.get(2));
        final int minorVersion = Integer.parseInt(tokens.get(1));

        String versionWithoutAppendix;
        final List<String> appendix = tokens.subList(3, tokens.size());

        if (microVersion != 0) {
            versionWithoutAppendix = String.join(".", tokens.subList(0, 2)) + "." +
                    (microVersion - 1);
        } else {
            final int newMicroVersionAfterMajorDowngrade = 9; //could be 0 as well, but we will fake this in the end
            if (minorVersion != 0) {
                versionWithoutAppendix = tokens.get(0) + "." +
                        (Integer.parseInt(tokens.get(1)) - 1) + "." +
                        newMicroVersionAfterMajorDowngrade;
            } else {
                final int newMinorVersionAfterMajorDowngrade = 9; //could be 0 as well, but we will fake this in the end
                versionWithoutAppendix = (Integer.parseInt(tokens.get(0)) - 1) + "." +
                        newMinorVersionAfterMajorDowngrade + "." +
                        newMicroVersionAfterMajorDowngrade;
            }
        }

        return appendix.isEmpty() ?
                versionWithoutAppendix :
                versionWithoutAppendix  + "." + String.join(".", tokens.subList(3, tokens.size()));
    }

}
