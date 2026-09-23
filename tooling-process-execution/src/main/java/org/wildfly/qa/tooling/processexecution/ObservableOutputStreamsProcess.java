package org.wildfly.qa.tooling.processexecution;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.exec.launcher.CommandLauncherFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Invoke a command line and make output streams observable.
 */
public class ObservableOutputStreamsProcess {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private final CommandLine commandLine;

    private final List<ProcessOutputObserver> standardOutputObservers;
    private final List<ProcessOutputObserver> errorOutputObservers;

    private final List<InteractiveOutputObserver> interactiveOutputObservers;

    private final List<ProcessControlObserver> processControlObservers;

    private final Path workDirectory;

    private final Map<String, String> environment;

    private final long timeout;
    private final TimeUnit timeoutTimeUnit;

    private final Pattern lineEndDelimiter;

    private ObservableOutputStreamsProcess(Builder builder) {
        this.commandLine = builder.commandLine;
        this.standardOutputObservers = builder.standardOutputObservers;
        this.errorOutputObservers = builder.errorOutputObservers;
        this.interactiveOutputObservers = builder.interactiveOutputObservers;
        this.processControlObservers = builder.processControlObservers;
        this.workDirectory = builder.workDirectory;
        this.environment = builder.environment;
        this.timeout = builder.timeout;
        this.timeoutTimeUnit = builder.timeoutTimeUnit;
        this.lineEndDelimiter = builder.lineEndDelimiter == null ?
                //standard line delimiter + "[y/N]" for occurrences of cases like "continue? [y\N]:"
                //we need positive lookbehind to match delimiter which immediately precedes current position to keep it in the match
                Pattern.compile("(" + System.lineSeparator() + "|(?<=(\\[y/N]:?)))") :
                builder.lineEndDelimiter;
    }

    /**
     * Invoke the process execution
     * Note: Obtaining result code on Windows is almost impossible so the check on return code should not be done there
     * @return return value of invoked process (not on Windows)
     */
    public int invoke() throws IOException, InterruptedException {
        final File workDir = this.workDirectory == null ? null : this.workDirectory.toFile();
        if (isWindows()) {
            this.environment.put("NOPAUSE", "true");
        }
        final Process process = CommandLauncherFactory.createVMLauncher()
                .exec(this.commandLine, this.environment, workDir);

        Future<?> stdoutFuture = this.executor.submit(() -> observeStream(process.getInputStream(), line -> {
            this.standardOutputObservers.forEach(observer -> observer.observe(line));
            this.interactiveOutputObservers.forEach(observer ->
                    observer.observeInteractively(line, process.getOutputStream()));
            this.processControlObservers.forEach(observer ->
                    observer.observeWithProcessControl(line, process));
        }));

        this.executor.execute(() ->
                observeStream(process.getErrorStream(), line ->
                        this.errorOutputObservers.forEach(observer -> observer.observe(line))));

        new ShutdownHookProcessDestroyer().add(process);

        try {
            //wait for process termination
            if (timeoutTimeUnit != null) {
                final boolean finishedBeforeTimeout = process.waitFor(this.timeout, this.timeoutTimeUnit);
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
                stdoutFuture.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw (cause instanceof RuntimeException) ? (RuntimeException) cause : new RuntimeException(cause);
            }
        } finally {
            //streams likely ended by now - let's shutdown the executor
            this.executor.shutdown();
        }

        return process.exitValue();
    }

    private void observeStream(final InputStream inputStream, final ProcessOutputLineProcessor processOutputLineProcessor) {
        final Scanner scanner = new Scanner(inputStream);
        //this has a downside of delimiter being trimmed from the output. TODO: use Scanner#match() to get delimiter
        scanner.useDelimiter(this.lineEndDelimiter);
        while (scanner.hasNext()) {
            //next() is used to match delimiter. Some lines are not ended by line separator since input is expected on the same line
            final String line = scanner.next();
            processOutputLineProcessor.processLine(line);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Builder for ObservableOutputStreamsProcess
     */
    public static final class Builder {

        private static final Logger logger = Logger.getLogger(ObservableOutputStreamsProcess.Builder.class.getSimpleName());

        private final CommandLine commandLine;

        private final List<ProcessOutputObserver> standardOutputObservers = new LinkedList<>();
        private final List<ProcessOutputObserver> errorOutputObservers = new LinkedList<>();

        private final List<InteractiveOutputObserver> interactiveOutputObservers = new LinkedList<>();

        private final List<ProcessControlObserver> processControlObservers = new LinkedList<>();

        private final Map<String, String> environment = new LinkedHashMap<>(System.getenv());

        private Path workDirectory;

        private long timeout;
        private TimeUnit timeoutTimeUnit;

        private Pattern lineEndDelimiter;

        /**
         * Create builder for ObservableOutputStreamsProcess. Sets default timeout 5 minutes in which the process is
         * expected to end.
         * @param commandLine command line to be started as an observable process
         */
        public Builder(final CommandLine commandLine) {
            this(commandLine, ObservableOutputStreamsProcess.isWindows() ? 15 : 5, TimeUnit.MINUTES);
        }

        /**
         * Creates builder for ObservableOutputStreamsProcess and allows to set custom timeout in which the process is
         * expected to end.
         * @param commandLine command line to be started as an observable process
         * @param timeout timeout in which the process is expected to end
         * @param timeUnit time unit for the timeout parameter
         */
        public Builder(final CommandLine commandLine, final long timeout, final TimeUnit timeUnit) {
            this.commandLine = commandLine;
            withTimeout(timeout, timeUnit);
        }

        /**
         * Add observer to the stdout of the process. This observer will see each line printed in the stdout.
         * @param observer an observer implementation
         * @return instance of this builder
         */
        public Builder addProcessOutputObserver(ProcessOutputObserver observer) {
            this.standardOutputObservers.add(observer);
            return this;
        }

        /**
         * Add observer to the stderr of the process. This observer will see each line printed in the stderr.
         * @param observer an observer implementation
         * @return instance of this builder
         */
        public Builder addErrorOutputObserver(ProcessOutputObserver observer) {
            this.errorOutputObservers.add(observer);
            return this;
        }

        /**
         * Add interactive observer to process. This observer will see each line printed in the stdout and can act upon
         * it according to its implementation.
         * @see org.wildfly.qa.tooling.processexecution.InteractiveOutputObserver
         * @param observer an observer implementation
         * @return instance of this builder
         */
        public Builder addInteractiveProcessOutputObserver(InteractiveOutputObserver observer) {
            this.interactiveOutputObservers.add(observer);
            return this;
        }

        /**
         * Add process control observer to process. This observer will see each line printed in the stdout and can act
         * upon it according to its implementation.
         * @see org.wildfly.qa.tooling.processexecution.ProcessControlObserver
         * @param observer an observer implementation
         * @return instance of this builder
         */
        public Builder addProcessControlObserver(ProcessControlObserver observer) {
            this.processControlObservers.add(observer);
            return this;
        }

        /**
         * A work directory in which the execution will be done
         * @param workDirectory a work directory
         * @return instance of this builder
         */
        public Builder workDirectory(Path workDirectory) {
            this.workDirectory = workDirectory;
            return this;
        }

        /**
         * Add an environment property to command execution
         * @param name name of property
         * @param value value of property
         * @return instance of this builder
         */
        public Builder addEnvironmentProperty(final String name, final String value) {
            this.environment.put(name, value);
            return this;
        }

        /**
         * Set timeout for process. This will cause forcible closing of process if it won't end before timeout.
         * @param timeout timeout
         * @param timeoutTimeUnit timeout unit
         * @return instance of this builder
         */
        public Builder withTimeout(final long timeout, final TimeUnit timeoutTimeUnit) {
            this.timeout = timeout;
            this.timeoutTimeUnit = Objects.requireNonNull(timeoutTimeUnit);
            return this;
        }

        /**
         * Set a pattern used to determine end of line in the process output
         * @param lineEndDelimiter a pattern identifying end of line
         * @return instance of this builder
         */
        public Builder lineEndDelimiter(Pattern lineEndDelimiter) {
            this.lineEndDelimiter = lineEndDelimiter;
            return this;
        }

        /**
         * Compose new instance of ObservableOutputStreamsProcess
         * @return instance of ObservableOutputStreamsProcess
         */
        public ObservableOutputStreamsProcess build() {
            logger.info("Command: " + this.commandLine);
            return new ObservableOutputStreamsProcess(this);
        }

    }


    @FunctionalInterface
    private interface ProcessOutputLineProcessor {

        void processLine(String line);

    }
}
