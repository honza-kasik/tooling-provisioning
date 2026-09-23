package org.wildfly.qa.tooling.mavenfaker;

/**
 * An exception thrown when faking of an artifact fails
 */
public class ArtifactInstallException extends Exception {

	public ArtifactInstallException(String message) {
		super(message);
	}

	public ArtifactInstallException(String message, Throwable cause) {
		super(message, cause);
	}

	public ArtifactInstallException(Throwable cause) {
		super(cause);
	}
}
