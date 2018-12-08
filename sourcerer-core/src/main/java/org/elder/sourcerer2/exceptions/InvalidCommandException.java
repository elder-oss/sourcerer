package org.elder.sourcerer2.exceptions;

/**
 * Thrown when a command is validated or attempted to be run, but is not in a valid state based on
 * the operation and currently provided configuration.
 */
public class InvalidCommandException extends IllegalStateException {
    public InvalidCommandException() {
    }

    public InvalidCommandException(final String s) {
        super(s);
    }

    public InvalidCommandException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public InvalidCommandException(final Throwable cause) {
        super(cause);
    }
}
