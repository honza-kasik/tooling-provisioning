package org.wildfly.qa.tooling.version;

/**
 * Version assume assumes next and previous version based on current version. This does not include default
 * implementation because based on context, the way of assumption may change.
 */
public interface VersionAssume {

    /**
     * Assume next version
     * @return newer version than current version
     */
    String nextVersion();

    /**
     * Assume previous version
     * @return older version than current version
     */
    String previousVersion();

}

