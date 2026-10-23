/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification.fluent;

import org.cyclonedx.exception.ParseException;
import org.cyclonedx.model.Bom;
import org.wildfly.qa.tooling.sbom.result.CheckCategory;
import org.wildfly.qa.tooling.sbom.result.CheckResult;
import org.wildfly.qa.tooling.sbom.verification.Context;
import org.wildfly.qa.tooling.sbom.verification.Lazy;
import org.wildfly.qa.tooling.sbom.result.CheckResults;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An installation that is expected to have a valid SBOM. Further conditions on the parsed SBOM can be added
 * with {@link #withBomCheck(Predicate, String)}.
 *
 * <p>The check that the SBOM exists and parses is implicit: it is added to the stack when this stage
 * switches to the next one or verifies. The stage is mutable: {@code withBomCheck} adds to its check stack
 * and returns the same stage.</p>
 */
public final class WithSbom {

    private final Context context;
    private final List<Supplier<CheckResult>> checks;

    public WithSbom(Context context, List<Supplier<CheckResult>> parentChecks) {
        this.context = context;
        this.checks = new ArrayList<>(parentChecks);
    }

    /**
     * Adds a condition on the parsed SBOM. The check fails with {@code errorMessage} when the predicate
     * returns {@code false}. If the SBOM is missing or invalid, the check fails for that reason and the
     * predicate is not called. A predicate that throws makes the check fail; it does not abort the
     * verification. The SBOM is parsed once and shared by all checks of this stage.
     *
     * @param predicate    the condition the SBOM must satisfy
     * @param errorMessage the issue reported when the condition is not met
     * @return this stage
     */
    public WithSbom withBomCheck(Predicate<Bom> predicate, String errorMessage) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(errorMessage, "errorMessage");
        checks.add(() -> checkBom(predicate, errorMessage));
        return this;
    }

    /**
     * Also requires the SBOM to conform to the CycloneDX JSON schema of the version it declares. Off by
     * default: the SBOM only has to parse, which accepts documents that strict consumers reject. A
     * schema-invalid SBOM is still parsed, so the other checks keep running. The failure lists the message of
     * every schema error.
     *
     * @return this stage
     */
    public WithSbom withSchemaValidation() {
        checks.add(this::checkSchema);
        return this;
    }

    /** Expects the installation to have a valid manifest. */
    public WithManifest withManifest() {
        return new WithManifest(context, stack());
    }

    /** Expects the installation to have no manifest. */
    public WithoutManifest withoutManifest() {
        return new WithoutManifest(context, stack());
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
    public List<Supplier<CheckResult>> stack() {
        List<Supplier<CheckResult>> stack = new ArrayList<>(checks);
        stack.add(this::checkSbomValid);
        return stack;
    }

    /** Fails if the SBOM is missing, cannot be read or is not valid CycloneDX. */
    private CheckResult checkSbomValid() {
        return sbomFailure().orElseGet(() -> CheckResults.passed(CheckCategory.SBOM));
    }

    /** Fails like {@link #checkSbomValid()}, or if the SBOM does not conform to the schema. */
    private CheckResult checkSchema() {
        Optional<CheckResult> failure = sbomFailure();
        if (failure.isPresent()) {
            return failure.get();
        }
        Path file = context.sbomFile().orElseThrow();
        Lazy<List<ParseException>> schemaErrors = context.schemaErrors();
        return schemaErrors.error()
                .map(error -> CheckResults.sbomInvalid(file, error))
                .orElseGet(() -> {
                    List<ParseException> errors = schemaErrors.get().orElseThrow();
                    return errors.isEmpty()
                            ? CheckResults.passed(CheckCategory.SBOM)
                            : CheckResults.sbomSchemaInvalid(file, errors);
                });
    }

    /** Fails like {@link #checkSbomValid()}, or if the predicate rejects the SBOM. */
    private CheckResult checkBom(Predicate<Bom> predicate, String errorMessage) {
        Optional<CheckResult> failure = sbomFailure();
        if (failure.isPresent()) {
            return failure.get();
        }
        boolean accepted;
        try {
            accepted = predicate.test(context.bom().get().orElseThrow());
        } catch (RuntimeException e) {
            return CheckResults.sbomCheckFailed(errorMessage + " (the check threw "
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
        return accepted
                ? CheckResults.passed(CheckCategory.SBOM)
                : CheckResults.sbomCheckFailed(errorMessage);
    }

    /** @return why the SBOM is not usable, or empty when it exists and parses */
    private Optional<CheckResult> sbomFailure() {
        Optional<Path> file = context.sbomFile();
        if (file.isEmpty()) {
            return Optional.of(CheckResults.sbomMissing(context.sbomPath()));
        }
        return context.bom().error().map(error -> CheckResults.sbomInvalid(file.get(), error));
    }
}
