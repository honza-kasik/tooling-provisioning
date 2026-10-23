/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification;

import org.cyclonedx.exception.ParseException;
import org.cyclonedx.model.Bom;
import org.cyclonedx.parsers.JsonParser;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.qa.tooling.sbom.result.CheckCategory;
import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.result.CheckResults;
import org.wildfly.qa.tooling.sbom.scanner.InstallationScanner;
import org.wildfly.qa.tooling.sbom.scanner.InstalledArtifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
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
 * to the first time a check needs the root ({@link #extractedFrom(Path, Path, Collection)}). The extracted directory is
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

    private final Lazy<Path> root;
    /** The zip the root is extracted from, or {@code null} when the root was given. */
    private final Path zip;
    /** Where the SBOM is, relative to the root (or absolute). */
    private final Path sbomFile;
    private final Lazy<Bom> bom = new Lazy<>(this::loadBom);
    private final Lazy<List<ParseException>> schemaErrors = new Lazy<>(this::loadSchemaErrors);
    private final Lazy<ChannelManifest> manifest = new Lazy<>(this::loadManifest);
    private final Lazy<Scan> scan = new Lazy<>(this::loadScan);
    /** What the JAR scan skips, relative to the root. */
    private final List<Path> scanExcluded;
    private volatile Path extractedDir;

    /**
     * @param root              the installation root (it need not exist yet; nothing is read until a check
     *                          needs it)
     * @param sbomFile          the SBOM, relative to {@code root} or absolute; gunzipped when its name ends
     *                          in {@code .gz}
     * @param excludedScanPaths what the JAR scan skips, see {@link InstallationScanner}
     */
    public Context(Path root, Path sbomFile, Collection<Path> excludedScanPaths) {
        this(Objects.requireNonNull(root, "root"), null, sbomFile, excludedScanPaths);
    }

    /** @param root the given root, or {@code null} if it is extracted from {@code zip} */
    private Context(Path root, Path zip, Path sbomFile, Collection<Path> excludedScanPaths) {
        this.zip = zip;
        this.sbomFile = Objects.requireNonNull(sbomFile, "sbomFile");
        this.root = zip == null ? new Lazy<>(() -> root) : new Lazy<>(() -> extract(zip));
        this.scanExcluded = List.copyOf(Objects.requireNonNull(excludedScanPaths, "excludedScanPaths"));
    }

    /**
     * A context whose root is a temporary directory that {@code zip} is extracted to when a check first
     * needs the root. The directory is deleted after {@link #run(List)}.
     */
    public static Context extractedFrom(Path zip, Path sbomFile, Collection<Path> excludedScanPaths) {
        return new Context(null, Objects.requireNonNull(zip, "zip"), sbomFile, excludedScanPaths);
    }

    /**
     * @return the installation root
     * @throws RuntimeException if the root is an extracted directory that could not be extracted or was
     *         already deleted; {@link #run(List)} turns this into a "not checked" result
     */
    public Path root() {
        Path dir = root.get().orElseThrow(() -> new RootUnavailableException(root.error().orElse("unknown cause")));
        if (zip != null && Files.notExists(dir)) {
            throw new RootUnavailableException("the extracted directory no longer exists");
        }
        return dir;
    }

    /** @return why the root could not be obtained (extraction failed), or empty when it could */
    public Optional<String> rootError() {
        return root.error();
    }

    /** @return where the SBOM is expected, whether or not the file exists */
    public Path sbomPath() {
        return root().resolve(sbomFile);
    }

    /** @return the SBOM file, or empty if it does not exist */
    public Optional<Path> sbomFile() {
        Path file = sbomPath();
        return Files.exists(file) ? Optional.of(file) : Optional.empty();
    }

    /** The parsed SBOM; its error is set when the file is missing, unreadable or not valid CycloneDX. */
    public Lazy<Bom> bom() {
        return bom;
    }

    /**
     * What the CycloneDX JSON schema rejects in the SBOM, one exception per violation; empty when the SBOM
     * conforms. Loaded only when asked for. Its error is set when the file is missing or unreadable.
     * {@link #bom()} is lenient and accepts documents that are rejected here.
     */
    public Lazy<List<ParseException>> schemaErrors() {
        return schemaErrors;
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
            return CheckResults.unavailable(CheckCategory.WILDFLY_ZIP, "the extracted WildFly");
        }
    }

    private void cleanUp() {
        if (zip == null) {
            return;
        }
        Path dir = extractedDir;
        extractedDir = null;
        if (dir != null) {
            deleteRecursively(dir);
        }
    }

    private Bom loadBom() throws Exception {
        return new JsonParser().parse(readSbom());
    }

    /** The schema is chosen by the library from the {@code specVersion} the document declares. */
    private List<ParseException> loadSchemaErrors() throws Exception {
        return new JsonParser().validate(readSbom());
    }

    /** @return the content of the SBOM file, gunzipped when it is {@code .gz} */
    private byte[] readSbom() throws IOException {
        Path file = sbomFile().orElseThrow(() -> new IllegalStateException("no SBOM file at " + sbomPath()));
        try (InputStream raw = Files.newInputStream(file);
             InputStream in = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
            return in.readAllBytes();
        }
    }

    private ChannelManifest loadManifest() throws Exception {
        return ChannelManifestMapper.fromString(Files.readString(manifestFile()));
    }

    private Scan loadScan() throws Exception {
        InstallationScanner scanner = new InstallationScanner(root(), scanExcluded);
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
