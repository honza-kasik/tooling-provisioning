package org.wildfly.qa.tooling.processexecution;

import org.apache.commons.exec.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CommandLine abstraction for executing JAR files.
 * 
 * This class provides a convenient way to create a CommandLine for running JAR files
 * with proper validation and java binary resolution.
 */
public class JarCommandLine extends CommandLine {

    private static final Path JAVA_HOME_BINARY = Paths.get(System.getProperty("java.home"), "bin", "java");

    /**
     * Create a new JarCommandLine with custom java binary and JAR file.
     * 
     * @param javaBinary path to the java binary to use
     * @param jar path to the JAR file to execute
     * @throws IllegalArgumentException if javaBinary or jar doesn't exist or is not a regular file
     */
    public JarCommandLine(final Path javaBinary, final Path jar) {
        super(javaBinary.toFile());
        checkFile(javaBinary);
        checkFile(jar);
        this.addArgument("-jar")
                .addArgument(jar.toAbsolutePath().toString());
    }

    /**
     * Create a new JarCommandLine with default java binary from java.home system property.
     * 
     * @param jar path to the JAR file to execute
     * @throws IllegalArgumentException if jar doesn't exist or is not a regular file
     */
    public JarCommandLine(final Path jar) {
        this(JAVA_HOME_BINARY, jar);
    }

    private void checkFile(final Path file) {
        if (file == null) {
            throw new IllegalArgumentException("File path cannot be null!");
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File '" + file.toAbsolutePath().toString() + "' doesn't exist!");
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File '" + file.toAbsolutePath().toString() + "' is not a file!");
        }
    }
}
