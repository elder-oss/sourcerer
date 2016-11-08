package org.elder.sourcerer.exceptions;

/**
 * Indicates that an error occurred while writing events to the underlying storage mechanism. The
 * failure is not a result of intermittent issues but rather the persistence engine has actively
 * indicated that writing the requested events is not possible, and guarantees that no events have
 * been written during the attempted operation.
 */
public class PermanentEventWriteException extends RuntimeException {
    public PermanentEventWriteException() {
    }

    public PermanentEventWriteException(final String message) {
        super(message);
    }

    public PermanentEventWriteException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public PermanentEventWriteException(final Throwable cause) {
        super(cause);
    }

    public PermanentEventWriteException(
            final String message,
            final Throwable cause,
            final boolean enableSuppression,
            final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
