package org.elder.sourcerer.exceptions;

/**
 * Indicates that an error occurred while writing events to the underlying storage mechanism, and
 * the outcome of the operation is undefined. The operation can be re-attempted and result in
 * a firm success or failure. Typical scenarios causing a retriable error includes network
 * disconnects and timeouts on high load.
 */
public class RetriableEventWriteException extends RuntimeException {
    public RetriableEventWriteException() {
    }

    public RetriableEventWriteException(final String message) {
        super(message);
    }

    public RetriableEventWriteException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public RetriableEventWriteException(final Throwable cause) {
        super(cause);
    }

    public RetriableEventWriteException(
            final String message,
            final Throwable cause,
            final boolean enableSuppression,
            final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
