package org.elder.sourcerer.exceptions;

import org.elder.sourcerer.ExpectedVersion;

/**
 * Thrown when two different expected newVersion requirements are given that conflict with each
 * other.
 */
public class ConflictingExpectedVersionsException extends IllegalArgumentException {
    private final ExpectedVersion baseVersion;
    private final ExpectedVersion newVersion;

    public ConflictingExpectedVersionsException(
            final String message,
            final ExpectedVersion baseVersion,
            final ExpectedVersion newVersion) {
        super(message);
        this.baseVersion = baseVersion;
        this.newVersion = newVersion;
    }

    public ExpectedVersion getBaseVersion() {
        return baseVersion;
    }

    public ExpectedVersion getNewVersion() {
        return newVersion;
    }
}
