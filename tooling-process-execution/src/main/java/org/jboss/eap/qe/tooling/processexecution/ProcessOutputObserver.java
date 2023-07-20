package org.jboss.eap.qe.tooling.processexecution;

/**
 * Classes of this interface are able to handle output of processes.
 * Eg of usage:
 * * logger::info
 * * outputLines::add
 *
 * This class was copied (and then slightly updated) from `tests-xp-manager` project, see the readme for the details
 */
public interface ProcessOutputObserver {

    void observe(String line);

}
