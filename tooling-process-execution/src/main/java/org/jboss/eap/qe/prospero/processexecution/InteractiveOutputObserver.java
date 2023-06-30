package org.jboss.eap.qe.prospero.processexecution;

import java.io.OutputStream;

/**
 * Classes those implements this interface are able to print some text to stdin
 * of processes handled by ObservableOutputStreamsProcess class.
 *
 * This class was copied (and then slightly updated) from `tests-xp-manager` project, see the readme for the details
 */
public interface InteractiveOutputObserver {

    void observeInteractively(final String line, final OutputStream outputStream);

}
