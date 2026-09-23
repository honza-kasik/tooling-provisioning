package org.wildfly.qa.tooling.maven;

/**
 * Maven goals constants. This is not an enum because Maven command line uses strings to identify goals and user can use
 * any name of goal.
 */
public final class MavenGoals {

    //prevent instantiation of this class
    private MavenGoals() {}

    public static final String CLEAN = "clean";

    public static final String PACKAGE = "package";

    public static final String INSTALL = "install";

}
