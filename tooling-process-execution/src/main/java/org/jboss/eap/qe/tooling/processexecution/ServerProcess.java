package org.jboss.eap.qe.tooling.processexecution;

import org.wildfly.extras.creaper.core.ManagementClient;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;
import org.wildfly.extras.creaper.core.online.OnlineOptions;
import org.wildfly.extras.creaper.core.online.operations.OperationException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction over server process - starting the process, executing a runnable in the right moment and destroying it.
 */
public class ServerProcess {

    private final ShCommandLine startScript;

    private final long timeout;
    private final TimeUnit timeUnit;

    /**
     * Create a new instance of server process. Instantiating won't create the server process. Default timeout of 5
     * minutes for the process is set. If you wish to have other timeout use
     * {@link ServerProcess#ServerProcess(ShCommandLine, long, TimeUnit)}.
     * @param startScript start script command line
     */
    public ServerProcess(final ShCommandLine startScript) {
        this(startScript, 5, TimeUnit.MINUTES);
    }

    /**
     * Create a new instance of server process. Instantiating won't create the server process.
     * @param startScript start script command line
     * @param timeout the maximum time to wait
     * @param timeUnit the time unit of the timeout parameter
     */
    public ServerProcess(final ShCommandLine startScript, long timeout, TimeUnit timeUnit) {
        this.startScript = startScript;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    /**
     * Start a server and run a runnable after the server started. Completed start is detected by "started in" line in
     * the server log. e.g. 12:03:45,777 INFO  [org.jboss.as] (Controller Boot Thread) WFLYSRV0025: JBoss EAP 7.4.2.GA
     * (WildFly Core 15.0.5.Final-redhat-00001) started in 2315ms - Started 317 of 556 services (343 services are lazy,
     * passive or on-demand)
     *
     * Please be aware that the timeout is set in constructor
     * @param runnable a runnable which should be run when server started. Server will be destroyed after runnable
     *                 completed.
     */
    public void runAfterServerStartedAndDestroyProcess(final ServerRunnable runnable) throws IOException, InterruptedException {
        runWhenLineAppearsInLogAndDestroyProcess("started in", runnable);
    }

    /**
     * Start a server and run a runnable after the deployment was deployed. Completed deployment is detected by
     * 'Deployed "<name>"' in the log. e.g. 12:07:31,023 INFO  [org.jboss.as.server] (management-handler-thread - 1)
     * WFLYSRV0010: Deployed "fooqixbar" (runtime-name : "qux")
     *
     * Please be aware that the timeout is set in constructor
     * @param deploymentName name of deployment which wil trigger runnable run.
     * @param runnable a runnable which should be run when server started. Server will be destroyed after runnable
     *                 completed.
     */
    public void runAfterDeploymentCompletedAndDestroyProcess(final String deploymentName, final ServerRunnable runnable) throws IOException, InterruptedException {
        runWhenLineAppearsInLogAndDestroyProcess("Deployed \"" + deploymentName + "\"", runnable);
    }

    private void runWhenLineAppearsInLogAndDestroyProcess(final String lineMatch, final ServerRunnable runnable) throws IOException, InterruptedException {
        new ObservableOutputStreamsProcess.Builder(startScript)
                .addProcessControlObserver((line, process) -> {
                    if (line.contains(lineMatch)) {
                        try (OnlineManagementClient client = ManagementClient.online(OnlineOptions.standalone()
                                .hostAndPort("localhost", 9990)
                                .build())) {
                            runnable.run(client);
                        } catch (IOException | OperationException | InterruptedException e) {
                            throw new ServerRunnableExecutionException(e);
                        } finally {
                            process.toHandle().children().forEach(ProcessHandle::destroy);
                        }
                    }
                })
                .withTimeout(this.timeout, this.timeUnit)
                .build()
                .invoke();
    }

    @FunctionalInterface
    public interface ServerRunnable {

        /**
         * Run a code with started server. The management client will be closed on upper level. Do not close it
         * manually.
         * @param managementClient online management client connected to a running server
         */
        void run(OnlineManagementClient managementClient) throws IOException, OperationException, InterruptedException;

    }

}
