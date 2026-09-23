package org.wildfly.qa.tooling.processexecution;

import org.apache.commons.exec.CommandLine;
import org.wildfly.extras.creaper.core.online.OnlineOptions;

import java.util.concurrent.TimeUnit;

/**
 * Abstraction over server domain process - starting the process, executing a runnable in the right moment and destroying it.
 */
public class DomainServerProcess extends ServerProcess {

    /**
     * Create a new instance of server process. Instantiating won't create the server process.
     * @param startScript start script command line
     * @param timeout the maximum time to wait
     * @param timeUnit the time unit of the timeout parameter
     * @param port connection port for management client
     * @param domainHostName domain host name
     */
    public DomainServerProcess(final CommandLine startScript, long timeout, TimeUnit timeUnit, int port, String domainHostName) {
        super(startScript, timeout, timeUnit, OnlineOptions.domain().forHost(domainHostName).forProfile("default").build().hostAndPort("localhost", port)
                .bootTimeout(30000)
                .connectionTimeout(30000)
                .build());
    }

}
