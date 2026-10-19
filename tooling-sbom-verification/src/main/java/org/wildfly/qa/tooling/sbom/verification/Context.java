/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification;

import org.cyclonedx.model.Bom;
import org.cyclonedx.parsers.JsonParser;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.result.CheckResults;
import org.wildfly.qa.tooling.sbom.scanner.InstallationScanner;
import org.wildfly.qa.tooling.sbom.scanner.InstalledArtifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What the stages of one verification chain share about the installation at one root: the root itself and
 * the data that is expensive to compute, the parsed SBOM, the parsed manifest and the JAR scan. Each is
 * computed at most once, on first use, no matter how many stages or checks need it.
 *
 * <p>The root is either given ({@link #Context(Path)}) or is a temporary directory that a zip is extracted
 * to the first time a check needs the root ({@link #extractedFrom(Path)}). The extracted directory is
 * deleted when {@link #run(List)} has run the checks, so such a context supports one run only; a later
 * run reports its checks as not checked.</p>
 */
public final class Context {

    /**
     * Result of walking the installation tree.
     *
     * @param artifacts  the JARs found
     * @param unreadable the paths that could not be read; when not empty, {@code artifacts} may be incomplete
     */
    public record Scan(List<InstalledArtifact> artifacts, List<String> unreadable) {}

    /** The root is an extracted directory that could not be extracted or is already deleted. */
    private static final class RootUnavailableException extends RuntimeException {
        RootUnavailableException(String message) {
            super(message);
        }
    }

    /** Name of the plain-JSON SBOM file in an installation root. */
    private static final String SBOM_FILE_NAME = "sbom.cdx.json";

    /** Name of the gzipped SBOM file, as packaged by WildFly builds. */
    private static final String SBOM_GZ_FILE_NAME = SBOM_FILE_NAME + ".gz";

    private final Lazy<Path> root;
    /** The zip the root is extracted from, or {@code null} when the root was given. */
    private final Path zip;
    private final Lazy<Bom> bom = new Lazy<>(this::loadBom);
    private final Lazy<ChannelManifest> manifest = new Lazy<>(this::loadManifest);
    private final Lazy<Scan> scan = new Lazy<>(this::loadScan);
    private volatile Path extractedDir;
    private volatile boolean closed;

    /** @param root the installation root (it need not exist yet; nothing is read until a check needs it) */
    public Context(Path root) {
        this(Objects.requireNonNull(root, "root"), null);
    }

    /** @param root the given root, or {@code null} if it is extracted from {@code zip} */
    private Context(Path root, Path zip) {
        this.zip = zip;
        this.root = zip == null ? new Lazy<>(() -> root) : new Lazy<>(() -> extract(zip));
    }

    /**
     * A context whose root is a temporary directory that {@code zip} is extracted to when a check first
     * needs the root. The directory is deleted after {@link #run(List)}.
     */
    public static Context extractedFrom(Path zip) {
        return new Context(null, Objects.requireNonNull(zip, "zip"));
    }

    /**
     * @return the installation root
     * @throws RuntimeException if the root is an extracted directory that could not be extracted or was
     *         already deleted; {@link #run(List)} turns this into a "not checked" result
     */
    public Path root() {
        if (closed) {
            throw new RootUnavailableException("the extracted directory was deleted after an earlier run");
        }
        return root.get().orElseThrow(() -> new RootUnavailableException(root.error().orElse("unknown cause")));
    }

    /** @return why the root could not be obtained (extraction failed), or empty when it could */
    public Optional<String> rootError() {
        return root.error();
    }

    /** @return the SBOM file of the installation, or empty if there is none */
    public Optional<Path> sbomFile() {
        Path plain = root().resolve(SBOM_FILE_NAME);
        if (Files.exists(plain)) {
            return Optional.of(plain);
        }
        Path gzipped = root().resolve(SBOM_GZ_FILE_NAME);
        return Files.exists(gzipped) ? Optional.of(gzipped) : Optional.empty();
    }

    /** The parsed SBOM; its error is set when the file is missing, unreadable or not valid CycloneDX. */
    public Lazy<Bom> bom() {
        return bom;
    }

    /** @return where the installation's manifest is, whether or not the file exists */
    public Path manifestFile() {
        return root().resolve(".installation").resolve("manifest.yaml");
    }

    /** The parsed manifest; its error is set when the file is missing, unreadable or not a valid manifest. */
    public Lazy<ChannelManifest> manifest() {
        return manifest;
    }

    /** The JARs on disk; its error is set when the installation tree cannot be walked at all. */
    public Lazy<Scan> scan() {
        return scan;
    }

    /**
     * Runs the checks in order, then deletes the extracted directory (if any), whatever happened. A check
     * that needs an unavailable root does not abort the run: it is reported as not checked, and the cause
     * is reported by the check that extracts the root.
     *
     * @return one result per check, in the order they ran
     */
    public List<CheckResult> run(List<Supplier<CheckResult>> checks) {
        try {
            return checks.stream().map(this::runOne).toList();
        } finally {
            cleanUp();
        }
    }

    private CheckResult runOne(Supplier<CheckResult> check) {
        try {
            return check.get();
        } catch (RootUnavailableException e) {
            return CheckResults.unavailable(CheckResults.WILDFLY_ZIP, "the extracted WildFly");
        }
    }

    private void cleanUp() {
        if (zip == null) {
            return;
        }
        closed = true;
        Path dir = extractedDir;
        extractedDir = null;
        if (dir != null) {
            deleteRecursively(dir);
        }
    }

    private Bom loadBom() throws Exception {
        Path file = sbomFile().orElseThrow(() -> new IllegalStateException("no SBOM file in " + root()));
        try (InputStream raw = Files.newInputStream(file);
             InputStream in = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
            return new JsonParser().parse(in);
        }
    }

    private ChannelManifest loadManifest() throws Exception {
        return ChannelManifestMapper.fromString(Files.readString(manifestFile()));
    }

    private Scan loadScan() throws Exception {
        InstallationScanner scanner = new InstallationScanner(root());
        List<InstalledArtifact> artifacts = scanner.scan();
        return new Scan(artifacts, scanner.unreadable());
    }

    private Path extract(Path zipFile) throws IOException {
        Path dir = Files.createTempDirectory("wildfly-sbom-check-").toAbsolutePath().normalize();
        // recorded before unzipping, so a half-extracted directory is deleted too
        extractedDir = dir;
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = dir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(dir)) {
                    throw new IOException("ZIP entry outside target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream in = zf.getInputStream(entry)) {
                        Files.copy(in, entryPath);
                    }
                }
            }
        }
        return dir;
    }

    /** Best effort: a file that cannot be deleted is left behind and does not hide the check results. */
    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // left behind
                }
            });
        } catch (IOException | RuntimeException ignored) {
            // left behind
        }
    }
}
