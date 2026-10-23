/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.verification;

import java.util.Optional;

/**
 * Computes a value once, on first access, and caches either the value or the reason it could not be
 * computed. A failed load is not retried.
 *
 * <p>Any {@link Exception} thrown by the loader, checked or unchecked, is caught and turned into an error
 * message: the exception class name followed by {@code ": "} and its message, or just the class name when
 * the message is {@code null}. {@link Error}s are not caught. Because unchecked exceptions are caught too, a
 * bug in the loader shows up as an error message, so the class name is kept in it.</p>
 *
 * <p>A loader that returns {@code null} counts as a failure: the value is never stored as {@code null}.
 * {@link #get()} is therefore empty exactly when {@link #error()} is present. Access is synchronized.</p>
 *
 * @param <T> the type of the computed value
 */
public final class Lazy<T> {

    /** Computes the value; may throw any exception. */
    @FunctionalInterface
    public interface Loader<T> {
        T load() throws Exception;
    }

    private final Loader<T> loader;
    private boolean loaded;
    private T value;
    private String error;

    public Lazy(Loader<T> loader) {
        this.loader = loader;
    }

    /** @return the value, or empty if it could not be computed */
    public synchronized Optional<T> get() {
        load();
        return Optional.ofNullable(value);
    }

    /** @return why the value could not be computed, or empty if it could */
    public synchronized Optional<String> error() {
        load();
        return Optional.ofNullable(error);
    }

    private void load() {
        if (loaded) {
            return;
        }
        try {
            T loadedValue = loader.load();
            if (loadedValue == null) {
                error = "loader returned null";
            } else {
                value = loadedValue;
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            error = describe(e);
        }
        loaded = true;
    }

    private static String describe(Exception e) {
        String name = e.getClass().getSimpleName();
        return e.getMessage() == null ? name : name + ": " + e.getMessage();
    }
}
