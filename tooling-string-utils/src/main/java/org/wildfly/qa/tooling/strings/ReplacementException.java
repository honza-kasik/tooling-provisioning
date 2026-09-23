package org.wildfly.qa.tooling.strings;

/**
 * Exception during string replacement
 */
public class ReplacementException extends Exception {

    /**
     * {@inheritDoc}
     */
    public ReplacementException() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    public ReplacementException(String message) {
        super(message);
    }

    /**
     * {@inheritDoc}
     */
    public ReplacementException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * {@inheritDoc}
     */
    public ReplacementException(Throwable cause) {
        super(cause);
    }
}
