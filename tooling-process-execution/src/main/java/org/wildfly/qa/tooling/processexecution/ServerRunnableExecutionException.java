package org.wildfly.qa.tooling.processexecution;

/**
 * Exception denoting issues when running a runnable with an observed process
 */
public class ServerRunnableExecutionException extends RuntimeException {

    /**
     * {@inheritDoc}
     */
    public ServerRunnableExecutionException(String message) {
        super(message);
    }

    /**
     * {@inheritDoc}
     */
    public ServerRunnableExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * {@inheritDoc}
     */
    public ServerRunnableExecutionException(Throwable cause) {
        super(cause);
    }

    /**
     * {@inheritDoc}
     */
    protected ServerRunnableExecutionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
