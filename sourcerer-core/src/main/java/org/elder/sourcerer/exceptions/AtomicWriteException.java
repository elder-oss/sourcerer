package org.elder.sourcerer.exceptions;

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
