package org.elder.sourcerer.crud;

/**
 * Thrown from the CommandUtils.executeSynchronously facade when the maximum number of retries have
 * been exhausted, indicating that, while a command was successfully executed, the relevant load
 * model was not updated within the expected period of time.
 */
public class ReadAssertFailedException extends RuntimeException {
    private static final long serialVersionUID = 1;

    public ReadAssertFailedException(final Throwable cause) {
        super(
                "Read model not updated in time.",
                cause);
    }
}
