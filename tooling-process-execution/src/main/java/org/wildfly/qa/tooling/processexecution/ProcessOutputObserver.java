package org.wildfly.qa.tooling.processexecution;

/**
 * Implementations of this interface are able to handle output of processes.
 * Eg of usage:
 * * logger::info
 * * outputLines::add
 */
public interface ProcessOutputObserver {

    /**
     * Observe a line from an output. This method should be called by the observer.
     * @param line line from the output.
     */
    void observe(String line);

}
