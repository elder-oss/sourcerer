package org.elder.sourcerer.crud;

import org.elder.sourcerer.CommandResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Utility methods for constructing a synchronous facade from independent but related methods. Used
 * to (re)create a traditional synchronous create/append operation on top of the command/query
 * pattern by first executing the command, then waiting until the entity appears on the load side.
 */
public final class CommandUtils {
    private static final Logger logger = LoggerFactory.getLogger(CommandUtils.class);

    private CommandUtils() {
    }

    /**
     * Waits for the presence of an entity in a load model, polling until the model asserts the
     * presence of the entity at the given version.
     *
     * @param assertFunction A that should complete without exceptions if the relevant load model
     *                       now has the entity with the given id and version. On any exception (but
     *                       not other throwables), this method will be retried up to the maximum
     *                       number of attempts specified, or until successful.
     * @throws ReadAssertFailedException if the maximum number of retries have been attempted and
     *                                   the assertion still fails.
     */
    public static void waitForUpdate(final Runnable assertFunction) {
        waitForUpdate(
                assertFunction,
                Duration.ofMillis(50),
                Duration.ofMillis(50),
                1.5,
                12);
    }

    /**
     * Waits for the presence of an entity in a load model, polling until the model asserts the
     * presence of the entity at the given version.
     *
     * @param assertFunction   A that should complete without exceptions if the relevant load model
     *                         now has the entity with the given id and version. On any exception
     *                         (but not other throwables), this method will be retried up to the
     *                         maximum number of attempts specified, or until successful.
     * @param initialDelay     The amount of time to wait between the command succeeding, and the
     *                         first attempt to call the assert function.
     * @param retryDelay       The amount of time to wait after the first failed attempt to assert
     *                         the presence of the aggregate, before retrying again.
     * @param retryDelayFactor For exponential backoff - the factor to use to increase the delay
     *                         between successive attempts. I.e. for a factor 2 and retry delay
     *                         100ms, the delays between attempts will be 100ms, 200ms, 400ms etc.
     * @param maxRetryAttempts The maximum number of attempts before giving up on the operation.
     * @throws ReadAssertFailedException if the maximum number of retries have been attempted and
     *                                   the assertion still fails.
     */
    public static void waitForUpdate(
            final Runnable assertFunction,
            final Duration initialDelay,
            final Duration retryDelay,
            final double retryDelayFactor,
            final int maxRetryAttempts) {
        sleepSoundly(initialDelay.toMillis());
        int attemptsLeft = maxRetryAttempts;
        long currentSleepMillis = initialDelay.toMillis();
        long currentRetryDelay = retryDelay.toMillis();

        Exception lastAssertError = null;
        do {
            sleepSoundly(currentSleepMillis);
            try {
                assertFunction.run();
                logger.debug("Assert successful");
                return;
            } catch (Exception ex) {
                logger.debug("Assert failed: {}", ex.getMessage());
                lastAssertError = ex;
                currentSleepMillis = (long) (currentRetryDelay * retryDelayFactor);
                currentRetryDelay = currentSleepMillis;
                attemptsLeft--;
            }
        }
        while (attemptsLeft > 0);

        logger.warn(
                "Retries exhausted after {} attempts, last error:",
                maxRetryAttempts,
                lastAssertError);
        throw new ReadAssertFailedException(lastAssertError);
    }

    /**
     * Executes a command, and waits for the affected entity to be updated in the relevant load
     * model. This method will execute the command once, propagating any exceptions immediately. If
     * the command is successful, it will use the provided assert function to check that it has been
     * created, retrying periodically as required until completed.
     *
     * @param commandFunction A function that, when called, executes the command and returns the id
     *                        of the affected entity along with its new version.
     * @param assertFunction  A function accepting an aggregate id and version that should complete
     *                        without exceptions if the relevant load model now has the entity with
     *                        the given id and version. On any exception (but not other throwables),
     *                        this method will be retried up to the maximum number of attempts
     *                        specified, or until successful.
     * @return The command response as returned from the command function.
     * @throws ReadAssertFailedException if the maximum number of retries have been attempted and
     *                                   the assertion still fails.
     */
    public static CommandResponse executeSynchronously(
            final Supplier<CommandResponse> commandFunction,
            final BiConsumer<String, Integer> assertFunction) {
        return executeSynchronously(
                commandFunction,
                assertFunction,
                Duration.ofMillis(50),
                Duration.ofMillis(50),
                1.5,
                12);
    }

    /**
     * Executes a command, and waits for the affected entity to be updated in the relevant load
     * model. This method will execute the command once, propagating any exceptions immediately. If
     * the command is successful, it will use the provided assert function to check that it has been
     * created, retrying periodically as required until completed.
     *
     * @param commandFunction  A function that, when called, executes the command and returns the id
     *                         of the affected entity along with its new version.
     * @param assertFunction   A function accepting an aggregate id and version that should complete
     *                         without exceptions if the relevant load model now has the entity with
     *                         the given id and version. On any exception (but not other
     *                         throwables), this method will be retried up to the maximum number of
     *                         attempts specified, or until successful.
     * @param initialDelay     The amount of time to wait between the command succeeding, and the
     *                         first attempt to call the assert function.
     * @param retryDelay       The amount of time to wait after the first failed attempt to assert
     *                         the presence of the aggregate, before retrying again.
     * @param retryDelayFactor For exponential backoff - the factor to use to increase the delay
     *                         between successive attempts. I.e. for a factor 2 and retry delay
     *                         100ms, the delays between attempts will be 100ms, 200ms, 400ms etc.
     * @param maxRetryAttempts The maximum number of attempts before giving up on the operation.
     * @return The command response as returned from the command function.
     * @throws ReadAssertFailedException if the maximum number of retries have been attempted and
     *                                   the assertion still fails.
     */
    public static CommandResponse executeSynchronously(
            final Supplier<CommandResponse> commandFunction,
            final BiConsumer<String, Integer> assertFunction,
            final Duration initialDelay,
            final Duration retryDelay,
            final double retryDelayFactor,
            final int maxRetryAttempts) {
        CommandResponse commandResponse = commandFunction.get();
        if (commandResponse.getNoOp() != null && commandResponse.getNoOp()) {
            logger.debug("Command was no-op, not waiting around");
            return commandResponse;
        }

        int expectedVersion;
        if (commandResponse.getPreviousVersion() != null) {
            expectedVersion = commandResponse.getPreviousVersion() + 1;
            logger.debug("Looking for version {} based on previous", expectedVersion);
        } else if (commandResponse.getNewVersion() != null) {
            expectedVersion = commandResponse.getNewVersion();
            logger.debug("Looking for version {} based on new", expectedVersion);
        } else {
            throw new IllegalArgumentException("Command was not no-op but returned no version!");
        }

        waitForUpdate(
                () -> assertFunction.accept(commandResponse.getId(), expectedVersion),
                initialDelay,
                retryDelay,
                retryDelayFactor,
                maxRetryAttempts);

        return commandResponse;
    }

    private static void sleepSoundly(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            logger.warn("Interrupted while sleeping, ignoring");
        }
    }
}
