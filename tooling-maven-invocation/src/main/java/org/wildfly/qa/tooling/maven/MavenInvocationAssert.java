package org.wildfly.qa.tooling.maven;

import java.util.List;

/**
 * Workaround for maven-invoker returning Integer.MIN_VALUE on JDK 25.
 * Falls back to checking Maven output for "BUILD SUCCESS".
 */
public class MavenInvocationAssert {

    public static void assertMavenSuccess(int exitCode, List<String> output) {
        assertMavenSuccess(exitCode, String.join(System.lineSeparator(), output));
    }

    public static void assertMavenSuccess(int exitCode, String output) {
        if (exitCode == 0) return;
        if (exitCode == Integer.MIN_VALUE && output.contains("BUILD SUCCESS")) return;
        throw new AssertionError("Maven build failed (exit code " + exitCode + "):\n" + output);
    }
}
