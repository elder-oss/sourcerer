package org.elder.sourcerer2.exceptions;

import org.elder.sourcerer2.ExpectedVersion;

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
