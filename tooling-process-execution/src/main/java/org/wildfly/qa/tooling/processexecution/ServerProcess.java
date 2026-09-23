package org.wildfly.qa.tooling.processexecution;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.exec.launcher.CommandLauncherFactory;
import org.wildfly.extras.creaper.core.ManagementClient;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;
import org.wildfly.extras.creaper.core.online.OnlineOptions;
import org.wildfly.extras.creaper.core.online.operations.OperationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction over server process - starting the process, executing a runnable in the right moment and destroying it.
 */
public class ServerProcess {

    private final CommandLine startScript;

    private final long timeout;
    private final TimeUnit timeUnit;

    private final OnlineOptions onlineOptions;

    /**
     * Create a new instance of server process. Instantiating won't create the server process. Timeout is read from
     * system property {@link ProcessExecutionProperties#SERVER_PROCESS_TIMEOUT_MINUTES} (default 5 minutes).
     * If you wish to have other timeout use {@link ServerProcess#ServerProcess(CommandLine, long, TimeUnit)}.
     * @param startScript start script command line
     */
    public ServerProcess(final CommandLine startScript) {
        this(startScript,
                ProcessExecutionProperties.serverProcessTimeoutMinutes(),
                TimeUnit.MINUTES);
    }

    /**
     * Create a new instance of server process. Instantiating won't create the server process.
     * @param startScript start script command line
     * @param timeout the maximum time to wait
     * @param timeUnit the time unit of the timeout parameter
     */
    public ServerProcess(final CommandLine startScript, long timeout, TimeUnit timeUnit) {
        this(startScript, timeout, timeUnit, OnlineOptions.standalone()
                .hostAndPort("localhost", 9990)
                .bootTimeout(30000) // timeouts are needed for use-case with restart
                .connectionTimeout(30000)
                .build());
    }

    /**
     * Create a new instance of server process. Instantiating won't create the server process.
     * @param startScript start script command line
     * @param timeout the maximum time to wait
     * @param timeUnit the time unit of the timeout parameter
     * @param onlineOptions options for server settings
     */
    protected ServerProcess(final CommandLine startScript, long timeout, TimeUnit timeUnit, OnlineOptions onlineOptions) {
        this.startScript = startScript;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.onlineOptions = onlineOptions;
    }

    /**
     * Start a server and run a runnable after the server started. Completed start is detected by "started in" line in
     * the server log. e.g. 12:03:45,777 INFO  [org.jboss.as] (Controller Boot Thread) WFLYSRV0025: WildFly 41.0.1.Final
     * (WildFly Core 23.0.3.Final) started in 2315ms - Started 317 of 556 services (343 services are lazy,
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
     * {@code 'Deployed "<name>"' in the log. e.g. 12:07:31,023 INFO  [org.jboss.as.server] (management-handler-thread
     * - 1) WFLYSRV0010: Deployed "fooqixbar" (runtime-name : "qux")}
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
                                .bootTimeout(30000) // timeouts are needed for use-case with restart
                                .connectionTimeout(30000)
                                .build())) {
                            runnable.run(client);
                        } catch (IOException | OperationException | InterruptedException e) {
                            throw new ServerRunnableExecutionException(e);
                        } finally {
                            process.toHandle().children().forEach(ProcessHandle::destroy);
                            process.destroy();
                        }
                    }
                })
                .withTimeout(this.timeout, this.timeUnit)
                .build()
                .invoke();
    }

    /**
     * Start a server and run a runnable after the deployment was deployed and client connected.
     *
     * @param runnable a runnable which should be run when server started. Server will be destroyed after runnable
     *                 completed.
     */
    public void runWhenClientConnectsAndDestroyProcess(final ServerRunnable runnable) throws IOException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        Map<String, String> environment = new LinkedHashMap<>(System.getenv());
        if (isWindows()) {
            environment.put("NOPAUSE", "true");
        }
        final Process process = CommandLauncherFactory.createVMLauncher()
                .exec(startScript, environment, null);

        executor.execute(() -> observeStream(process.getInputStream()));
        executor.execute(() -> observeStream(process.getErrorStream()));

        Future<?> callbackFuture = executor.submit(() -> {
                    try (OnlineManagementClient client = ManagementClient.online(onlineOptions)) {
                        runnable.run(client);
                    } catch (IOException | OperationException | InterruptedException e) {
                        throw new ServerRunnableExecutionException(e);
                    } finally {
                        process.toHandle().children().forEach(ProcessHandle::destroy);
                    }
                }
        );

        new ShutdownHookProcessDestroyer().add(process);

        try {
            //wait for process termination
            if (timeUnit != null) {
                final boolean finishedBeforeTimeout = process.waitFor(this.timeout, this.timeUnit);
                if (!finishedBeforeTimeout) {
                    process.toHandle().children().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    throw new InterruptedException("Process was destroyed before timeout elapsed. Timeout is either " +
                            "too low or we have an issue there. The process might hang waiting on input.");
                }
            } else {
                process.waitFor();
            }

            try {
                callbackFuture.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw (cause instanceof RuntimeException) ? (RuntimeException) cause : new RuntimeException(cause);
            }
        } finally {
            //streams likely ended by now - lets shutdown the executor
            executor.shutdown();
        }
    }

    private void observeStream(final InputStream inputStream) {
        final Scanner scanner = new Scanner(inputStream);
        while (scanner.hasNext()) {
            scanner.next();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Functional interface encapsulating method which code runs upon satisfaction of a condition
     */
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
