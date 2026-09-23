package org.wildfly.qa.tooling.processexecution;

import org.apache.commons.exec.CommandLine;
import org.wildfly.extras.creaper.core.online.OnlineOptions;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction over bootable JAR server process.
 * 
 * This class provides methods to start a bootable JAR server and execute operations
 * after the server has started.
 */
public class BootableJarProcess extends ServerProcess {

    /**
     * Create a new instance of bootable JAR process with default timeout of 5 minutes.
     * 
     * @param bootableJarPath path to the bootable JAR file
     */
    public BootableJarProcess(final Path bootableJarPath) {
        this(bootableJarPath, 5, TimeUnit.MINUTES);
    }

    /**
     * Create a new instance of bootable JAR process with custom timeout.
     * 
     * @param bootableJarPath path to the bootable JAR file
     * @param timeout the maximum time to wait for server startup
     * @param timeUnit the time unit of the timeout parameter
     */
    public BootableJarProcess(final Path bootableJarPath, long timeout, TimeUnit timeUnit) {
        this(bootableJarPath, timeout, timeUnit, OnlineOptions.standalone()
                .hostAndPort("localhost", 9990)
                .bootTimeout(30000)
                .connectionTimeout(30000)
                .build());
    }

    /**
     * Create a new instance of bootable JAR process with custom timeout and online options.
     * 
     * @param bootableJarPath path to the bootable JAR file
     * @param timeout the maximum time to wait for server startup
     * @param timeUnit the time unit of the timeout parameter
     * @param onlineOptions options for management client connection
     */
    protected BootableJarProcess(final Path bootableJarPath, long timeout, TimeUnit timeUnit, 
                                   OnlineOptions onlineOptions) {
        super(new JarCommandLine(bootableJarPath), timeout, timeUnit, onlineOptions);
    }
}
