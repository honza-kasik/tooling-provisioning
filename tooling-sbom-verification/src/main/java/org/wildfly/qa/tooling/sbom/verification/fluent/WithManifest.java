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

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An installation that is expected to have a valid {@code .installation/manifest.yaml}. Further conditions
 * on the manifest can be added with {@link #withManifestCheck(Predicate, String)}.
 *
 * <p>The check that the manifest exists and parses is implicit: it is added to the stack when this stage
 * switches to the next one or verifies. The stage is mutable: {@code withManifestCheck} adds to its check
 * stack and returns the same stage.</p>
 */
public final class WithManifest {

    private final Context context;
    private final List<Supplier<CheckResult>> checks;

    WithManifest(Context context, List<Supplier<CheckResult>> parentChecks) {
        this.context = context;
        this.checks = new ArrayList<>(parentChecks);
    }

    /**
     * Adds a condition on the parsed manifest. The check fails with {@code errorMessage} when the predicate
     * returns {@code false}. If the manifest is missing or invalid, the check fails for that reason and the
     * predicate is not called. A predicate that throws makes the check fail; it does not abort the
     * verification. The manifest is parsed once and shared by all checks of this stage.
     *
     * @param predicate    the condition the manifest must satisfy
     * @param errorMessage the issue reported when the condition is not met
     * @return this stage
     */
    public WithManifest withManifestCheck(Predicate<ChannelManifest> predicate, String errorMessage) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(errorMessage, "errorMessage");
        checks.add(() -> checkManifest(predicate, errorMessage));
        return this;
    }

    /**
     * Adds the check that every installed maven component of the SBOM is in the manifest, with the same
     * version. Shaded (POM-derived) components are not compared, because they are bundled inside another
     * JAR and are not installed on their own; see {@link WithDiskChecks#withShadedCheck}.
     *
     * <p>Each component that has no stream in the manifest, or a stream with another version, is one
     * issue. If the SBOM or the manifest is unavailable, the check fails as "not checked"; the cause is
     * reported by the SBOM check and the manifest check.</p>
     *
     * @return this stage
     */
    public WithManifest withSbomMatchingManifest() {
        checks.add(this::checkSbomMatchesManifest);
        return this;
    }

    /** Compares the SBOM with the JARs on disk. */
    public WithDiskChecks withDiskChecks() {
        return new WithDiskChecks(context, stack());
    }

    /**
     * Runs the checks of the parents and of this stage.
     *
     * @return one result per check, in the order they ran; never {@code null}
     */
    public List<CheckResult> verify() {
        return context.run(stack());
    }

    /** @return the checks collected so far followed by the implicit check of this stage; a new list */
    private List<Supplier<CheckResult>> stack() {
        List<Supplier<CheckResult>> stack = new ArrayList<>(checks);
        stack.add(this::checkManifestPresent);
        return stack;
    }

    /** Fails if the manifest is missing, cannot be read or cannot be parsed as a channel manifest. */
    private CheckResult checkManifestPresent() {
        return manifestFailure().orElseGet(() -> CheckResults.passed(CheckCategory.MANIFEST));
    }

    /** Fails like {@link #checkManifestPresent()}, or if the predicate rejects the manifest. */
    private CheckResult checkManifest(Predicate<ChannelManifest> predicate, String errorMessage) {
        Optional<CheckResult> failure = manifestFailure();
        if (failure.isPresent()) {
            return failure.get();
        }
        boolean accepted;
        try {
            accepted = predicate.test(context.manifest().get().orElseThrow());
        } catch (RuntimeException e) {
            return CheckResults.manifestCheckFailed(errorMessage + " (the check threw "
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
        return accepted
                ? CheckResults.passed(CheckCategory.MANIFEST)
                : CheckResults.manifestCheckFailed(errorMessage);
    }

    /** Fails for every installed SBOM component that the manifest lacks or lists with another version. */
    private CheckResult checkSbomMatchesManifest() {
        Optional<Bom> bom = context.bom().get();
        if (bom.isEmpty()) {
            return CheckResults.unavailable(CheckCategory.SBOM_VS_MANIFEST, "the SBOM");
        }
        Optional<ChannelManifest> manifest = context.manifest().get();
        if (manifest.isEmpty()) {
            return CheckResults.unavailable(CheckCategory.SBOM_VS_MANIFEST, "the manifest");
        }
        List<String> issues = new ArrayList<>();
        for (Component c : ComponentUtils.collectMavenComponents(bom.get().getComponents())) {
            if (ComponentUtils.componentKind(c) == ComponentKind.POM_DERIVED) {
                continue;
            }
            String coordinates = c.getGroup() + ":" + c.getName();
            Optional<String> manifestVersion = manifest.get().findStreamFor(c.getGroup(), c.getName())
                    .map(org.wildfly.channel.Stream::getVersion);
            if (manifestVersion.isEmpty()) {
                issues.add("NOT IN MANIFEST " + coordinates + " (sbom: " + c.getVersion() + ")");
            } else if (!manifestVersion.get().equals(c.getVersion())) {
                issues.add("VERSION MISMATCH " + coordinates
                        + " sbom=" + c.getVersion() + " manifest=" + manifestVersion.get());
            }
        }
        return CheckResults.sbomVsManifest(issues);
    }

    /** @return why the manifest is not usable, or empty when it exists and parses */
    private Optional<CheckResult> manifestFailure() {
        if (!Files.exists(context.manifestFile())) {
            return Optional.of(CheckResults.manifestMissing(context.root()));
        }
        return context.manifest().error()
                .map(error -> CheckResults.manifestInvalid(context.manifestFile(), error));
    }
}
