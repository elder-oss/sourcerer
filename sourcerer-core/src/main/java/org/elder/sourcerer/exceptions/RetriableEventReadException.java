package org.elder.sourcerer.exceptions;

/**
 * Indicates that an error occurred while reading events from the underlying storage mechanism. The
 * operation can be re-attempted and result in a firm success or failure. Typical scenarios causing
 * a retriable error includes network disconnects and timeouts on high load.
 */
public class RetriableEventReadException extends RuntimeException {
    public RetriableEventReadException() {
    }

    public RetriableEventReadException(final String message) {
        super(message);
    }

    public RetriableEventReadException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public RetriableEventReadException(final Throwable cause) {
        super(cause);
    }

    public RetriableEventReadException(
            final String message,
            final Throwable cause,
            final boolean enableSuppression,
            final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
