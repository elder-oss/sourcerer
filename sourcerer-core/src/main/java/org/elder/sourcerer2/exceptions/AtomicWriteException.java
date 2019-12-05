package org.elder.sourcerer2.exceptions;

/**
 * Thrown when event stream was updated between reading the existing stream and writing new events.
 */
public class AtomicWriteException extends UnexpectedVersionException {
    public AtomicWriteException(final UnexpectedVersionException ex) {
        super(
                ex.getMessage(),
                ex,
                ex.getCurrentVersion(),
                ex.getExpectedVersion()
        );
    }
}
