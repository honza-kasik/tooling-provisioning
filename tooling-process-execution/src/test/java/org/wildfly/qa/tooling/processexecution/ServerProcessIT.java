package org.wildfly.qa.tooling.processexecution;

import org.apache.commons.exec.CommandLine;
import org.wildfly.extras.creaper.core.online.operations.Address;
import org.wildfly.extras.creaper.core.online.operations.Operations;
import org.wildfly.extras.creaper.core.online.operations.admin.Administration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link ServerProcess}. Requires a WildFly installation unpacked at the path specified by the
 * {@code wildfly.home} system property. Run with:
 * <pre>
 *     mvn verify -pl tooling-process-execution -Pintegration-tests
 * </pre>
 *
 * <p>Tests cover both entry points ({@link ServerProcess#runAfterServerStartedAndDestroyProcess} and
 * {@link ServerProcess#runWhenClientConnectsAndDestroyProcess}), verifying that callbacks execute, management
 * operations work, and — critically — that exceptions thrown inside callbacks propagate to the caller thread
 * instead of being silently swallowed.</p>
 */
class ServerProcessIT {

    private static final long TIMEOUT = 2;
    private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;

    private static Path standaloneScript;

    @BeforeAll
    static void setUp() {
        String home = System.getProperty("wildfly.home");
        assertNotNull(home, "wildfly.home system property must be set");
        Path wildflyHome = Paths.get(home);
        assertTrue(wildflyHome.toFile().isDirectory(), "WildFly home must exist: " + wildflyHome);
        standaloneScript = wildflyHome.resolve("bin/standalone.sh");
        assertTrue(standaloneScript.toFile().exists(), "standalone.sh must exist: " + standaloneScript);
    }

    /**
     * Verify that {@link ServerProcess#runAfterServerStartedAndDestroyProcess} invokes the callback after the server
     * logs "started in".
     *
     * <p>Passes when the callback executes and the launcher and server JVM have exited before the helper returns.</p>
     */
    @Test
    void runAfterServerStarted_callbackExecutes() throws IOException, InterruptedException {
        ServerProcess serverProcess = new ServerProcess(
                new ShCommandLine(standaloneScript), TIMEOUT, TIMEOUT_UNIT);

        AtomicBoolean executed = new AtomicBoolean(false);
        List<ProcessHandle> processes = new ArrayList<>();

        serverProcess.runAfterServerStartedAndDestroyProcess(client -> {
            processes.addAll(ProcessHandle.current().descendants().collect(Collectors.toList()));
            assertNotNull(client);
            executed.set(true);
        });

        assertTrue(executed.get(), "Callback should have been invoked after server started");
        assertProcessesStopped(processes);
    }

    /**
     * Runs the log-triggered callback through a launcher that writes more than a pipe buffer during shutdown.
     * Passes when output keeps draining and every launched process exits before the helper returns.
     */
    @Test
    void runAfterServerStarted_drainsShutdownOutput() throws Exception {
        assertNoisyLauncherStops(false);
    }

    /**
     * Throws from a log-triggered callback while shutdown still needs to drain output.
     * Passes when the original callback exception is reported and all launched processes have exited.
     */
    @Test
    void runAfterServerStarted_callbackFailureStillDrainsShutdownOutput() throws Exception {
        assertNoisyLauncherStops(true);
    }

    private static void assertNoisyLauncherStops(boolean failCallback) throws Exception {
        CommandLine command = new CommandLine(Paths.get(System.getProperty("java.home"), "bin", "java").toFile())
                .addArgument("-cp").addArgument(System.getProperty("java.class.path"), false)
                .addArgument(NoisyLauncher.class.getName())
                .addArgument(standaloneScript.toString(), false);
        ServerProcess serverProcess = new ServerProcess(command, TIMEOUT, TIMEOUT_UNIT);
        List<ProcessHandle> processes = new ArrayList<>();
        ServerProcess.ServerRunnable callback = client -> {
            processes.addAll(ProcessHandle.current().descendants().collect(Collectors.toList()));
            if (failCallback) {
                throw new IOException("callback failed");
            }
        };
        try {
            if (failCallback) {
                ServerRunnableExecutionException failure = assertThrows(ServerRunnableExecutionException.class,
                        () -> serverProcess.runAfterServerStartedAndDestroyProcess(callback));
                assertInstanceOf(IOException.class, failure.getCause());
                assertEquals("callback failed", failure.getCause().getMessage());
            } else {
                serverProcess.runAfterServerStartedAndDestroyProcess(callback);
            }
            assertProcessesStopped(processes);
        } finally {
            processes.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        }
    }

    /**
     * Verify that a checked exception thrown inside the {@code runAfterServerStartedAndDestroyProcess} callback
     * propagates as a {@link ServerRunnableExecutionException} to the caller.
     */
    @Test
    void runAfterServerStarted_callbackExceptionPropagates() {
        ServerProcess serverProcess = new ServerProcess(
                new ShCommandLine(standaloneScript), TIMEOUT, TIMEOUT_UNIT);

        ServerRunnableExecutionException thrown = assertThrows(
                ServerRunnableExecutionException.class,
                () -> serverProcess.runAfterServerStartedAndDestroyProcess(client -> {
                    throw new IOException("intentional test exception");
                })
        );

        assertInstanceOf(IOException.class, thrown.getCause());
        assertEquals("intentional test exception", thrown.getCause().getMessage());
    }

    /**
     * Verify that {@link ServerProcess#runWhenClientConnectsAndDestroyProcess} invokes the callback once the
     * management client connects.
     *
     * <p>Passes when the callback executes and the helper terminates the server without a management shutdown.</p>
     */
    @Test
    void runWhenClientConnects_callbackExecutes() throws IOException, InterruptedException {
        ServerProcess serverProcess = new ServerProcess(
                new ShCommandLine(standaloneScript), TIMEOUT, TIMEOUT_UNIT);

        AtomicBoolean executed = new AtomicBoolean(false);

        serverProcess.runWhenClientConnectsAndDestroyProcess(client -> {
            assertNotNull(client);
            executed.set(true);
        });

        assertTrue(executed.get(), "Callback should have been invoked after client connected");
    }

    /**
     * Verify that a checked exception thrown inside the {@code runWhenClientConnectsAndDestroyProcess} callback
     * propagates as a {@link ServerRunnableExecutionException} to the caller. Prior to the {@code executor.submit()}
     * fix, this exception was silently swallowed.
     */
    @Test
    void runWhenClientConnects_callbackExceptionPropagates() {
        ServerProcess serverProcess = new ServerProcess(
                new ShCommandLine(standaloneScript), TIMEOUT, TIMEOUT_UNIT);

        ServerRunnableExecutionException thrown = assertThrows(
                ServerRunnableExecutionException.class,
                () -> serverProcess.runWhenClientConnectsAndDestroyProcess(client -> {
                    throw new IOException("intentional test exception");
                })
        );

        assertInstanceOf(IOException.class, thrown.getCause());
        assertEquals("intentional test exception", thrown.getCause().getMessage());
    }

    /**
     * Verify that a management operation can be executed inside the {@code runWhenClientConnectsAndDestroyProcess}
     * callback and the result is visible to the caller.
     *
     * <p>Passes when the operation reads the running state and the helper terminates the server after the callback.</p>
     */
    @Test
    void runWhenClientConnects_managementOperationWorks() throws Exception {
        ServerProcess serverProcess = new ServerProcess(
                new ShCommandLine(standaloneScript), TIMEOUT, TIMEOUT_UNIT);

        AtomicReference<String> serverState = new AtomicReference<>();

        serverProcess.runWhenClientConnectsAndDestroyProcess(client -> {
            Operations ops = new Operations(client);
            serverState.set(ops.readAttribute(Address.root(), "server-state").stringValue());
        });

        assertEquals("running", serverState.get(), "Server should have reported 'running' state");
    }

    /**
     * Exercises the CI failure scenario: read the server state and explicitly shut down through management.
     *
     * <p>Passes when the state is running, the shutdown call returns and the helper completes without timing out.</p>
     */
    @Test
    void runWhenClientConnects_managementOperationAndExplicitShutdownWork() throws Exception {
        ServerProcess serverProcess = new ServerProcess(
                new ShCommandLine(standaloneScript), TIMEOUT, TIMEOUT_UNIT);
        AtomicReference<String> serverState = new AtomicReference<>();
        AtomicBoolean shutdownReturned = new AtomicBoolean(false);

        serverProcess.runWhenClientConnectsAndDestroyProcess(client -> {
            Operations operations = new Operations(client);
            serverState.set(operations.readAttribute(Address.root(), "server-state").stringValue());
            new Administration(client).shutdown();
            shutdownReturned.set(true);
        });

        assertEquals("running", serverState.get(), "Server should have reported 'running' state");
        assertTrue(shutdownReturned.get(), "The management shutdown should have returned");
    }

    /**
     * Runs a management callback against a directly launched JVM, with no shell child to terminate.
     *
     * <p>Passes when the callback reads the running state and the process is destroyed after it returns,
     * without requiring the callback to shut down the server.</p>
     */
    @Test
    void runWhenClientConnects_destroysDirectJvmAfterCallback() throws Exception {
        Path home = standaloneScript.getParent().getParent();
        CommandLine command = new CommandLine(Paths.get(System.getProperty("java.home"), "bin", "java").toFile())
                .addArgument("-Djboss.home.dir=" + home, false)
                .addArgument("-Dorg.jboss.boot.log.file=" + home.resolve("standalone/log/server.log"), false)
                .addArgument("-Dlogging.configuration=" + home.resolve("standalone/configuration/logging.properties").toUri(), false)
                .addArgument("-jar")
                .addArgument(home.resolve("jboss-modules.jar").toString(), false)
                .addArgument("-mp")
                .addArgument(home.resolve("modules").toString(), false)
                .addArgument("org.jboss.as.standalone");
        ServerProcess serverProcess = new ServerProcess(command, TIMEOUT, TIMEOUT_UNIT);
        AtomicReference<String> serverState = new AtomicReference<>();

        serverProcess.runWhenClientConnectsAndDestroyProcess(client -> {
            Operations operations = new Operations(client);
            serverState.set(operations.readAttribute(Address.root(), "server-state").stringValue());
        });

        assertEquals("running", serverState.get());
    }

    private static void assertProcessesStopped(List<ProcessHandle> processes) {
        assertFalse(processes.isEmpty(), "The callback should observe the running server process");
        for (ProcessHandle process : processes) {
            assertFalse(process.isAlive(), "Server process is still alive after cleanup: " + process.pid());
        }
    }

    /** Launches WildFly alongside a slowly stopping child, and writes a full pipe of output during shutdown. */
    public static class NoisyLauncher {

        public static void main(String[] args) throws Exception {
            if (args.length == 0) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
                System.out.println("READY");
                Thread.sleep(Long.MAX_VALUE);
            } else {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("x".repeat(256 * 1024))));
                Process child = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp", System.getProperty("java.class.path"), NoisyLauncher.class.getName())
                        .redirectError(ProcessBuilder.Redirect.INHERIT).start();
                BufferedReader readiness = new BufferedReader(new InputStreamReader(child.getInputStream()));
                if (!"READY".equals(readiness.readLine())) {
                    throw new IOException("Child failed to start");
                }
                Process server = new ProcessBuilder("sh", args[0]).inheritIO().start();
                server.waitFor();
                child.waitFor();
            }
        }
    }
}
