/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.scanner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Scans the entire WildFly installation root for JAR files.
 *
 * Every {@code *.jar} found anywhere under the installation root is
 * collected, except for files under the excluded paths. By default that is
 * {@code .installation/} (the Prospero internal cache, not part of the runtime
 * installation); see {@link #DEFAULT_EXCLUDED}.
 *
 * No filename parsing is performed. The raw filename is stored as-is so
 * that callers can construct their own expected filename from authoritative
 * metadata (e.g. a CycloneDX SBOM component's {@code name} and
 * {@code version}) and compare directly, avoiding the ambiguity of splitting
 * artifact ids that themselves contain digit-prefixed segments (e.g.
 * {@code wildfly-clustering-session-spec-servlet-6.0-5.0.11.Final.jar}).
 *
 * Paths that cannot be read are not skipped silently: after a scan they are available from
 * {@link #unreadable()}, so a caller can tell that the returned list may be incomplete.
 */
public class InstallationScanner {

    /** The paths a scan skips unless told otherwise, relative to the installation root. */
    public static final List<Path> DEFAULT_EXCLUDED = List.of(Path.of(".installation"));

    private final Path installRoot;
    /** Absolute and normalized; a directory is skipped when it is, or is under, one of these. */
    private final List<Path> excluded;
    private final List<String> unreadable = new ArrayList<>();

    /**
     * A scanner that skips {@link #DEFAULT_EXCLUDED}.
     *
     * @param installRoot path to the WildFly installation root (the directory
     *                    that contains {@code jboss-modules.jar},
     *                    {@code modules/}, {@code bin/}, etc.).
     */
    public InstallationScanner(Path installRoot) {
        this(installRoot, DEFAULT_EXCLUDED);
    }

    /**
     * A scanner that skips exactly the given paths; {@link #DEFAULT_EXCLUDED} is not added. To keep it, pass
     * it along with the others.
     *
     * @param installRoot path to the installation root
     * @param excluded    directories (and everything under them) that are not scanned, each relative to
     *                    {@code installRoot}; an absolute path is used as it is. A path that does not exist
     *                    excludes nothing.
     */
    public InstallationScanner(Path installRoot, Collection<Path> excluded) {
        this.installRoot = Objects.requireNonNull(installRoot, "installRoot");
        this.excluded = Objects.requireNonNull(excluded, "excluded").stream()
                .map(path -> installRoot.resolve(path).toAbsolutePath().normalize())
                .toList();
    }

    /**
     * Walks the installation tree and returns every JAR found, except those
     * under the excluded paths. The paths this scan could not read are available
     * afterwards from {@link #unreadable()}; those of an earlier scan are discarded.
     *
     * @return unmodifiable list of discovered artifacts
     * @throws IOException if the file tree cannot be walked
     */
    public List<InstalledArtifact> scan() throws IOException {
        List<InstalledArtifact> result = new ArrayList<>();
        unreadable.clear();

        Files.walkFileTree(installRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path normalized = dir.toAbsolutePath().normalize();
                if (excluded.stream().anyMatch(normalized::startsWith)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".jar")) {
                    result.add(new InstalledArtifact(
                            file.getFileName().toString(),
                            file.toAbsolutePath().toString()
                    ));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                unreadable.add(file.toAbsolutePath() + " (" + exc.getClass().getSimpleName() + ")");
                return FileVisitResult.CONTINUE;
            }
        });

        return Collections.unmodifiableList(result);
    }

    /**
     * @return paths the last {@link #scan()} could not read, each with the exception type; empty before
     *         the first scan. When not empty, the scanned list may be incomplete.
     */
    public List<String> unreadable() {
        return Collections.unmodifiableList(new ArrayList<>(unreadable));
    }
}
