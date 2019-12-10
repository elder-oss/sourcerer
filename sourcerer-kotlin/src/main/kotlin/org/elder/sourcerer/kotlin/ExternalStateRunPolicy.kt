package org.elder.sourcerer.kotlin

import org.elder.sourcerer.SubscriptionWorkerRunPolicy
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Run Policy that defers to a run checker for whether the subscription worker should run.
 *
 * If the checker fails then use the last result. If the last result is older than
 * MAX_AGE_LAST_RESULT, we reach Unknown territory.
 *
 * During Unknown territory, we will let the worker run. If the worker receives a
 * MutualExclusionConflictException, we don't let it run until at least MIN_TIME_REATTEMPT has
 * passed, at which point we let one run again. If status check succeeds at any time during Unknown
 * territory, we move back to Known territory.
 *
 * NOTE: This piece of code would live in Elder rather than in Sourcerer.
 */
class ExternalStateRunPolicy(
        private val shouldRunChecker: () -> Boolean
) : SubscriptionWorkerRunPolicy {
    private var state: State = State.KnownState(true, Instant.MIN)

    override fun shouldRun(): Boolean {
        val (shouldRun, newState) = state.evaluate(shouldRunChecker, Instant.now())
        state = newState

        return shouldRun
    }

    override fun lastRunSuccessful() {
        this.state = state.lastRunSuccessful(Instant.now())
    }

    override fun lastRunFailed(ex: Exception) {
        this.state = state.lastRunFailed(ex, Instant.now())
    }

    sealed class State {
        /**
         * Evaluate and update state.
         *
         * Returns a pair of (shouldRun, newState).
         */
        abstract fun evaluate(
                executeShouldRun: () -> Boolean,
                timestamp: Instant
        ): Pair<Boolean, State>

        open fun lastRunSuccessful(timestamp: Instant): State = this
        open fun lastRunFailed(ex: Exception, timestamp: Instant): State = this

        init {
            logger.info("Current state: $this")
        }

        /**
         * We believe we know the current external state. As long as the last successful external
         * state check succeeded less than MAX_AGE_LAST_RESULT, we consider it known state.
         */
        data class KnownState(
                private val shouldRunLastResult: Boolean,
                private val shouldRunLastUpdated: Instant
        ) : State() {
            override fun evaluate(
                    executeShouldRun: () -> Boolean,
                    timestamp: Instant
            ): Pair<Boolean, State> {
                return try {
                    val shouldRun = executeShouldRun()
                    return shouldRun to KnownState(shouldRun, timestamp)
                } catch (ex: RuntimeException) {
                    logger.warn("Updating outcome failed", ex)
                    val timeSinceSuccessfulCheck = Duration.between(shouldRunLastUpdated, timestamp)
                    if (timeSinceSuccessfulCheck <= MAX_AGE_LAST_RESULT) {
                        // Not long ago that we successfully checked state. Last was RUN.
                        shouldRunLastResult to this
                    } else {
                        // Grace period of last value failed, we are at the mercy of the
                        // consumer now.
                        true to UnknownState(shouldRunLastResult, Instant.MIN)
                    }
                }
            }
        }

        /**
         * We don't know the current external state.
         *
         * At this point all we can do is carefully try to run and look out for consumer conflict
         * exceptions. When we see these exception, fallback for some time before trying again.
         */
        data class UnknownState(
                val lastRunSuccessful: Boolean,
                val lastRunAttempt: Instant
        ) : State() {
            override fun evaluate(
                    executeShouldRun: () -> Boolean,
                    timestamp: Instant
            ): Pair<Boolean, State> {
                val timeSinceLastRunAttempt = Duration.between(lastRunAttempt, timestamp)
                return if (lastRunSuccessful || timeSinceLastRunAttempt >= MIN_TIME_REATTEMPT) {
                    try {
                        val shouldRun = executeShouldRun()
                        shouldRun to KnownState(shouldRun, timestamp)
                    } catch (ex: RuntimeException) {
                        logger.warn("Updating outcome failed", ex)
                        lastRunSuccessful to this
                    }
                } else {
                    // Last run failed and it was not that long ago.
                    false to this
                }
            }

            override fun lastRunSuccessful(timestamp: Instant) =
                    UnknownState(true, timestamp)

            // Only MutualExclusionConflictException is considered a run failure as it indicates
            // conflicting consumers.
            override fun lastRunFailed(ex: Exception, timestamp: Instant) =
                    UnknownState(ex is MutualExclusionConflictException, timestamp)
        }
    }

    companion object {
        private val MAX_AGE_LAST_RESULT = Duration.of(1, ChronoUnit.MINUTES)
        private val MIN_TIME_REATTEMPT = Duration.of(1, ChronoUnit.MINUTES)
        private val logger = LoggerFactory.getLogger(ExternalStateRunPolicy::class.java)
    }
}

class MutualExclusionConflictException : RuntimeException()
