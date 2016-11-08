package org.elder.sourcerer;

/**
 * Implemented by command post processors, allow for horizontal customization of commands after
 * they have been created by a command factory, but before returned to the caller.
 */
public interface CommandPostProcessor {
    void postProcessCommand(Command<?, ?, ?> command);
}
