package org.wildfly.qa.tooling.processexecution;

/**
 * An interface describing process observer which has capability to access whole process, thus control it.
 */
public interface ProcessControlObserver {

    /**
     * Observe new line printed by process
     * @param line line printed to output
     * @param process process which printed the line
     */
    void observeWithProcessControl(final String line, final Process process);

}
