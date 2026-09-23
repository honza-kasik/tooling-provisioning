package org.wildfly.qa.tooling.processexecution;

import org.wildfly.extras.creaper.core.online.operations.Address;
import org.wildfly.extras.creaper.core.online.operations.Operations;
import org.wildfly.extras.creaper.core.online.operations.admin.Administration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     */
    @Test
    void runAfterServerStarted_callbackExecutes() throws IOException, InterruptedException {
        ServerProcess serverProcess = new ServerProcess(
                new ShCommandLine(standaloneScript), TIMEOUT, TIMEOUT_UNIT);

        AtomicBoolean executed = new AtomicBoolean(false);

        serverProcess.runAfterServerStartedAndDestroyProcess(client -> {
            assertNotNull(client);
            executed.set(true);
        });

        assertTrue(executed.get(), "Callback should have been invoked after server started");
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
     */
    @Test
    void runWhenClientConnects_callbackExecutes() throws IOException, InterruptedException {
        ServerProcess serverProcess = new ServerProcess(
                new ShCommandLine(standaloneScript), TIMEOUT, TIMEOUT_UNIT);

        AtomicBoolean executed = new AtomicBoolean(false);

        serverProcess.runWhenClientConnectsAndDestroyProcess(client -> {
            assertNotNull(client);
            executed.set(true);
            new Administration(client).shutdown();
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
     */
    @Test
    void runWhenClientConnects_managementOperationWorks() throws Exception {
        ServerProcess serverProcess = new ServerProcess(
                new ShCommandLine(standaloneScript), TIMEOUT, TIMEOUT_UNIT);

        AtomicReference<String> serverState = new AtomicReference<>();

        serverProcess.runWhenClientConnectsAndDestroyProcess(client -> {
            Operations ops = new Operations(client);
            serverState.set(ops.readAttribute(Address.root(), "server-state").stringValue());
            new Administration(client).shutdown();
        });

        assertEquals("running", serverState.get(), "Server should have reported 'running' state");
    }
}
