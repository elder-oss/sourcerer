package org.elder.sourcerer2.exceptions;

/**
 * Indicates that an error occurred while reading events from the underlying storage mechanism. The
 * failure is not a result of intermittent issues but rather the persistence engine has actively
 * indicated that reading the requested events is not possible.
 */
public class PermanentEventReadException extends RuntimeException {
    public PermanentEventReadException() {
    }

    public PermanentEventReadException(final String message) {
        super(message);
    }

    public PermanentEventReadException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public PermanentEventReadException(final Throwable cause) {
        super(cause);
    }

    public PermanentEventReadException(
            final String message,
            final Throwable cause,
            final boolean enableSuppression,
            final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
