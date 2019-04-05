package org.elder.sourcerer.exceptions;

import org.elder.sourcerer.ExpectedVersion;

/**
 * Thrown when event stream was updated between reading the existing stream and writing new events.
 *
 * The update can be retried.
 */
public class AtomicWriteException extends UnexpectedVersionException {
    public AtomicWriteException(
            final Integer currentVersion,
            final ExpectedVersion expectedVersion
    ) {
        super(currentVersion, expectedVersion);
    }

    public AtomicWriteException(
            final String message,
            final Integer currentVersion,
            final ExpectedVersion expectedVersion
    ) {
        super(message, currentVersion, expectedVersion);
    }

    public AtomicWriteException(
            final String message,
            final Throwable cause,
            final Integer currentVersion,
            final ExpectedVersion expectedVersion
    ) {
        super(message, cause, currentVersion, expectedVersion);
    }

    public AtomicWriteException(
            final Throwable cause,
            final Integer currentVersion,
            final ExpectedVersion expectedVersion
    ) {
        super(cause, currentVersion, expectedVersion);
    }
}
