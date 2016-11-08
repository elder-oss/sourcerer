package org.elder.sourcerer.exceptions;

import org.elder.sourcerer.ExpectedVersion;

/**
 * Throw to indicate that the current version of a stream does not match the expected one.
 */
public class UnexpectedVersionException extends IllegalStateException {
    private final Integer currentVersion;
    private final ExpectedVersion expectedVersion;

    public UnexpectedVersionException(
            final Integer currentVersion,
            final ExpectedVersion expectedVersion) {
        this(formatDefaultMessage(currentVersion, expectedVersion), currentVersion,
             expectedVersion);
    }

    public UnexpectedVersionException(
            final String message,
            final Integer currentVersion,
            final ExpectedVersion expectedVersion) {
        super(message);
        this.currentVersion = currentVersion;
        this.expectedVersion = expectedVersion;
    }

    public UnexpectedVersionException(
            final String message,
            final Throwable cause,
            final Integer currentVersion,
            final ExpectedVersion expectedVersion) {
        super(message, cause);
        this.currentVersion = currentVersion;
        this.expectedVersion = expectedVersion;
    }

    public UnexpectedVersionException(
            final Throwable cause,
            final Integer currentVersion,
            final ExpectedVersion expectedVersion) {
        this(formatDefaultMessage(currentVersion, expectedVersion), cause, currentVersion,
             expectedVersion);
    }

    /**
     * Gets the current version of the stream - null indicating that no current stream exists.
     */
    public Integer getCurrentVersion() {
        return currentVersion;
    }

    /**
     * Gets the expected version asserted that conflicts with the current one.
     */
    public ExpectedVersion getExpectedVersion() {
        return expectedVersion;
    }

    private static String formatDefaultMessage(
            final Integer currentVersion,
            final ExpectedVersion expectedVersion) {
        return String.format("Existing stream version %s does not match expected %s",
                             currentVersion, expectedVersion);
    }
}
