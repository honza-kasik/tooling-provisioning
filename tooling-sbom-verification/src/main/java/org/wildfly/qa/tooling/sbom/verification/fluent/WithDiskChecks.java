/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification.fluent;

import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.qa.tooling.sbom.component.ComponentKind;
import org.wildfly.qa.tooling.sbom.component.ComponentUtils;
import org.wildfly.qa.tooling.sbom.result.CheckCategory;
import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.verification.Context;
import org.wildfly.qa.tooling.sbom.result.CheckResults;
import org.wildfly.qa.tooling.sbom.result.ShadedResult;
import org.wildfly.qa.tooling.sbom.scanner.InstalledArtifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Comparison of the SBOM with the JARs on disk, in both directions. Reachable only after the manifest
 * expectation has been stated, which in turn requires an SBOM.
 *
 * <p>The two comparisons are implicit: they are added to the stack when {@link #verify()} runs. If the SBOM
 * or the JAR scan is unavailable, a comparison fails with a "not checked" issue; the cause is reported by the
 * SBOM check and the installed-JARs check.</p>
 *
 * <p>Shaded artifacts are not checked implicitly, because a WildFly distribution legitimately ships some.
 * State what is expected with {@link #withShadedCheck(Predicate, String)}.</p>
 */
public final class WithDiskChecks {

    /**
     * The default for {@link #withFatJarNames(Map)}: Maven artifacts that are installed under a fixed,
     * unversioned filename.
     * Key: SBOM component {@code name}; value: path relative to the installation root.
     */
    public static final Map<String, String> DEFAULT_FAT_JAR_NAMES = Map.of(
            "wildfly-launcher", "bin/launcher.jar",
            "jboss-modules", "jboss-modules.jar");

    /**
     * The default for {@link #withGenericFatJars(Collection)}: assembled fat JARs that the SBOM declares as
     * {@code pkg:generic} components, by their path relative to the installation root.
     */
    public static final Set<Path> DEFAULT_GENERIC_FAT_JARS = Set.of(
            Path.of("bin/client/jboss-cli-client.jar"),
            Path.of("bin/client/jboss-client.jar"),
            Path.of("bin/wildfly-elytron-tool.jar"));

    /** Stands in for the manifest of an installation that has none: it has no streams. */
    private static final ChannelManifest NO_MANIFEST = new ChannelManifest(null, null, null, List.of());

    private final Context context;
    private final List<Supplier<CheckResult>> checks;
    private Map<String, String> fatJarNames = DEFAULT_FAT_JAR_NAMES;
    private Set<Path> genericFatJars = DEFAULT_GENERIC_FAT_JARS;

    WithDiskChecks(Context context, List<Supplier<CheckResult>> parentChecks) {
        this.context = context;
        this.checks = new ArrayList<>(parentChecks);
    }

    /**
     * Replaces {@link #DEFAULT_FAT_JAR_NAMES}: the Maven artifacts that are installed under a fixed,
     * unversioned filename instead of {@code <artifactId>-<version>.jar}. The SBOM-versus-disk check looks
     * for such an artifact at the given path, and the disk-versus-SBOM check accepts the file name of that
     * path as declared. To add to the defaults, copy them into the map you pass.
     *
     * @param fatJarNames SBOM component {@code name} to path relative to the installation root
     * @return this stage
     */
    public WithDiskChecks withFatJarNames(Map<String, String> fatJarNames) {
        this.fatJarNames = Map.copyOf(Objects.requireNonNull(fatJarNames, "fatJarNames"));
        return this;
    }

    /**
     * Replaces {@link #DEFAULT_GENERIC_FAT_JARS}: the assembled fat JARs that the SBOM declares as
     * {@code pkg:generic} components instead of Maven artifacts. The disk-versus-SBOM check accepts a JAR at
     * one of these paths only if the SBOM has a {@code pkg:generic} component with an occurrence at exactly
     * that path; otherwise the JAR is reported as undeclared, like any other. A listed path that is not on
     * disk is ignored. To add to the defaults, copy them into the collection you pass.
     *
     * @param paths the JARs, relative to the installation root
     * @return this stage
     */
    public WithDiskChecks withGenericFatJars(Collection<Path> paths) {
        this.genericFatJars = Objects.requireNonNull(paths, "paths").stream()
                .map(Path::normalize)
                .collect(Collectors.toUnmodifiableSet());
        return this;
    }

    /**
     * Adds a condition on the shaded artifacts of the SBOM: the POM-derived components, which are bundled
     * inside a parent JAR and so have no file of their own. The {@link ShadedResult} holds those of them
     * that are <em>not tracked</em>: no JAR of that name is on disk, and the manifest has no stream for
     * them or a stream with another version. They are grouped by the purl of their parent component. If the
     * installation has no manifest, none of them is tracked.
     *
     * <p>The check fails with {@code errorMessage} when the predicate returns {@code false}. If the SBOM,
     * the JAR scan or an existing manifest is unavailable, it fails as "not checked" and the predicate is
     * not called. A predicate that throws makes the check fail; it does not abort the verification.</p>
     *
     * @param predicate    the condition the untracked shaded artifacts must satisfy
     * @param errorMessage the issue reported when the condition is not met
     * @return this stage
     */
    public WithDiskChecks withShadedCheck(Predicate<ShadedResult> predicate, String errorMessage) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(errorMessage, "errorMessage");
        checks.add(() -> checkShaded(predicate, errorMessage));
        return this;
    }

    /**
     * Runs the checks of the parents and of this stage.
     *
     * @return one result per check, in the order they ran; never {@code null}
     */
    public List<CheckResult> verify() {
        return context.run(stack());
    }

    /** @return the checks collected so far followed by the checks of this stage; a new list */
    private List<Supplier<CheckResult>> stack() {
        List<Supplier<CheckResult>> stack = new ArrayList<>(checks);
        stack.add(this::checkSbomVsDisk);
        stack.add(this::checkDiskVsSbom);
        return stack;
    }

    /**
     * Fails for every SBOM component whose JAR, native library directory or other file is not on disk.
     * A JAR is looked up by its file name in the scan, and every occurrence the SBOM declares for a
     * component (Maven or {@code pkg:generic}) must exist, so a component with several locations is checked
     * at all of them.
     */
    private CheckResult checkSbomVsDisk() {
        Optional<Bom> bom = context.bom().get();
        if (bom.isEmpty()) {
            return CheckResults.unavailable(CheckCategory.SBOM_VS_DISK, "the SBOM");
        }
        List<Component> mavenComponents = ComponentUtils.collectMavenComponents(bom.get().getComponents());
        Optional<Set<String>> diskFileNames = diskFileNames();
        if (diskFileNames.isEmpty()) {
            return CheckResults.unavailable(CheckCategory.SBOM_VS_DISK, "the JAR scan");
        }
        Path root = context.root();
        List<String> missing = new ArrayList<>();
        for (Component c : mavenComponents) {
            switch (ComponentUtils.componentKind(c)) {
                case POM_DERIVED -> { /* nothing on disk to check */ }
                case NATIVE_LIB -> ComponentUtils.nativeLibDirs(c).forEach(libDir -> {
                    if (!Files.isDirectory(root.resolve(libDir))) {
                        missing.add(c.getPurl() + " [native lib dir missing: " + libDir + "]");
                    }
                });
                case OTHER_FILE -> checkOccurrences(c, root, null, missing);
                case JAR -> {
                    String fatJar = fatJarNames.get(c.getName());
                    String expected = fatJar != null ? fatJar : ComponentUtils.expectedJarFileName(c);
                    boolean found = fatJar != null
                            ? Files.exists(root.resolve(fatJar))
                            : diskFileNames.get().contains(expected);
                    if (!found) {
                        missing.add(c.getPurl() + " [expected: " + expected + "]");
                    }
                    // a JAR that was not found is already reported; do not report its occurrence again
                    checkOccurrences(c, root, found ? null : Path.of(expected).getFileName().toString(), missing);
                }
            }
        }
        for (Component c : ComponentUtils.collectGenericComponents(bom.get().getComponents())) {
            checkOccurrences(c, root, null, missing);
        }
        return CheckResults.sbomVsDisk(missing);
    }

    /**
     * Adds an issue for every distinct occurrence of the component that does not exist under the root.
     *
     * @param alreadyReported the file name of a location that was reported by another check, or {@code null}
     */
    private static void checkOccurrences(Component c, Path root, String alreadyReported, List<String> missing) {
        ComponentUtils.occurrenceLocations(c).stream().distinct()
                .filter(location -> !Path.of(location).getFileName().toString().equals(alreadyReported))
                .filter(location -> !Files.exists(root.resolve(location)))
                .forEach(location -> missing.add(c.getPurl() + " [file missing: " + location + "]"));
    }

    /** Fails for every JAR on disk that the SBOM does not declare, once per file name. */
    private CheckResult checkDiskVsSbom() {
        Optional<Bom> optionalBom = context.bom().get();
        if (optionalBom.isEmpty()) {
            return CheckResults.unavailable(CheckCategory.DISK_VS_SBOM, "the SBOM");
        }
        Bom bom = optionalBom.get();
        List<Component> mavenComponents = ComponentUtils.collectMavenComponents(bom.getComponents());
        Optional<Context.Scan> scan = context.scan().get();
        if (scan.isEmpty()) {
            return CheckResults.unavailable(CheckCategory.DISK_VS_SBOM, "the JAR scan");
        }
        Set<String> sbomFileNames = mavenComponents.stream()
                .filter(c -> ComponentUtils.componentKind(c) != ComponentKind.POM_DERIVED)
                .filter(c -> ComponentUtils.componentKind(c) != ComponentKind.OTHER_FILE)
                .map(c -> {
                    String fatJar = fatJarNames.get(c.getName());
                    return fatJar != null
                            ? Path.of(fatJar).getFileName().toString()
                            : ComponentUtils.expectedJarFileName(c);
                })
                .collect(Collectors.toSet());

        Set<Path> declaredGeneric = new HashSet<>(ComponentUtils.genericLocations(bom.getComponents()));
        declaredGeneric.retainAll(genericFatJars);
        Path root = context.root().toAbsolutePath().normalize();

        Set<String> reported = new HashSet<>();
        List<String> undeclared = scan.get().artifacts().stream()
                .filter(a -> !sbomFileNames.contains(a.jarFileName()))
                .filter(a -> !declaredGeneric.contains(root.relativize(Path.of(a.jarPath()).toAbsolutePath().normalize())))
                .map(InstalledArtifact::jarFileName)
                .filter(reported::add)
                .toList();
        return CheckResults.diskVsSbom(undeclared);
    }

    /** Fails like the disk checks when an input is unavailable, or if the predicate rejects the result. */
    private CheckResult checkShaded(Predicate<ShadedResult> predicate, String errorMessage) {
        Optional<Bom> bom = context.bom().get();
        if (bom.isEmpty()) {
            return CheckResults.unavailable(CheckCategory.SHADED, "the SBOM");
        }
        Optional<Set<String>> diskFileNames = diskFileNames();
        if (diskFileNames.isEmpty()) {
            return CheckResults.unavailable(CheckCategory.SHADED, "the JAR scan");
        }
        ChannelManifest manifest = NO_MANIFEST;
        if (Files.exists(context.manifestFile())) {
            Optional<ChannelManifest> parsed = context.manifest().get();
            if (parsed.isEmpty()) {
                return CheckResults.unavailable(CheckCategory.SHADED, "the manifest");
            }
            manifest = parsed.get();
        }
        boolean accepted;
        try {
            accepted = predicate.test(collectShaded(bom.get(), diskFileNames.get(), manifest));
        } catch (RuntimeException e) {
            return CheckResults.shadedCheckFailed(errorMessage + " (the check threw "
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
        return accepted
                ? CheckResults.passed(CheckCategory.SHADED)
                : CheckResults.shadedCheckFailed(errorMessage);
    }

    /** The POM-derived components that no JAR on disk and no manifest stream accounts for, by parent purl. */
    private ShadedResult collectShaded(Bom bom, Set<String> diskFileNames, ChannelManifest manifest) {
        Map<String, String> parentIndex = ComponentUtils.parentIndex(bom.getComponents());
        Map<String, List<String>> grouped = new TreeMap<>();
        ComponentUtils.collectMavenComponents(bom.getComponents()).stream()
                .filter(c -> ComponentUtils.componentKind(c) == ComponentKind.POM_DERIVED)
                .filter(c -> !diskFileNames.contains(ComponentUtils.expectedJarFileName(c)))
                .filter(c -> {
                    String fatJar = fatJarNames.get(c.getName());
                    return fatJar == null || !diskFileNames.contains(Path.of(fatJar).getFileName().toString());
                })
                .filter(c -> manifest.findStreamFor(c.getGroup(), c.getName())
                        .map(org.wildfly.channel.Stream::getVersion)
                        .map(v -> !v.equals(c.getVersion()))
                        .orElse(true))
                .forEach(c -> grouped
                        .computeIfAbsent(parentIndex.getOrDefault(c.getPurl(), "(no parent)"), k -> new ArrayList<>())
                        .add(c.getPurl()));
        grouped.values().forEach(Collections::sort);
        return new ShadedResult(Collections.unmodifiableMap(grouped));
    }

    /** @return the file names of the JARs on disk, or empty if the scan is unavailable */
    private Optional<Set<String>> diskFileNames() {
        return context.scan().get().map(scan -> scan.artifacts().stream()
                .map(InstalledArtifact::jarFileName)
                .collect(Collectors.toSet()));
    }
}
