package org.wildfly.qa.tooling.processexecution;

import java.io.OutputStream;

/**
 * Implementations of this interface are able to print some text to stdin of processes handled by
 * ObservableOutputStreamsProcess class.
 * @see ObservableOutputStreamsProcess
 */
public interface InteractiveOutputObserver {

    /**
     * Observe a line from a process and optionally use its output stream
     * @param line a line from the output
     * @param outputStream a process output stream
     */
    void observeInteractively(final String line, final OutputStream outputStream);

}
