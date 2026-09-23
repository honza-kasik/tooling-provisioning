package org.wildfly.qa.tooling.processexecution;

import org.apache.commons.exec.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A boilerplate for making the shell script center of rotation, so we don't have to specify binary each time.
 * Be aware that WildFly starts child process from script. To successfully destroy child processes you need to
 * execute {@code process.toHandle().children().forEach(ProcessHandle::destroy)}.
 */
public class ShCommandLine extends CommandLine {

    /**
     * Create new instance
     * @param shellScriptPath path to the shell script
     */
    public ShCommandLine(final Path shellScriptPath) {
        super("sh");
        checkFile(shellScriptPath);
        this.addArgument(shellScriptPath.toAbsolutePath().toString());
    }

    private void checkFile(final Path file) {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File '" + file.toAbsolutePath() + "' doesn't exist!");
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File '" + file.toAbsolutePath() + "' is not a file!");
        }
    }

}

