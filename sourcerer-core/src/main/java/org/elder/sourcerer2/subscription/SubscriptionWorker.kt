package org.elder.sourcerer2.subscription

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.elder.sourcerer2.EventRecord
import org.elder.sourcerer2.EventRepository
import org.elder.sourcerer2.EventSubscriptionHandler
import org.elder.sourcerer2.EventSubscriptionPositionSource
import org.elder.sourcerer2.EventSubscriptionUpdate
import org.elder.sourcerer2.SubscriptionWorkerConfig
import org.slf4j.LoggerFactory

internal class SubscriptionWorker<T>(
        private val repository: EventRepository<T>,
        private val shard: Int?,
        private val positionSource: EventSubscriptionPositionSource,
        private val handler: EventSubscriptionHandler<T>,
        private val config: SubscriptionWorkerConfig
) {
    private var retryCount = 0

    suspend fun run() {
        while (true) {
            try {
                logger.debug("Subscription session starting")
                runOneSession()
                // We're still here meaning the subscription marked completion, clean exit
            } catch (ex: Exception) {
                logger.warn("Exception in subscription, retry logic will apply", ex)
                if (ex is CancellationException || !handler.handleError(ex, retryCount)) {
                    // We're cancelled or have givem up, parent will deal with it
                    throw ex
                }

                sleepForRetry(retryCount++)
                logger.info("Subscription restarting after error")
                handler.subscriptionRestarting()
            }
        }
    }

    private suspend fun runOneSession() {
        val subscriptionPosition = positionSource.subscriptionPosition

        logger.info("Subscribing to event repository from {}", subscriptionPosition)
        repository.subscribe(subscriptionPosition, shard, config.batchSize).consume {
            while (processUpdates(this)) {
                logger.debug("Processed updates, will do more")
                retryCount = 0
            }
        }
    }

    private suspend fun processUpdates(updatesQueue: ReceiveChannel<EventSubscriptionUpdate<T>>): Boolean {
        val updateBatch = getUpdateBatch(updatesQueue) ?: return false

        // Updates can be a mix of events and control messages, we want to batch consecutive
        // event records, but need to handle control messages individually
        var events: MutableList<EventRecord<T>>? = null
        for (update in updateBatch) {
            when (update) {
                is EventSubscriptionUpdate.CaughtUp -> {
                    logger.debug("Subscription caught up, processing pending events")
                    processEventsIfAny(events)
                    events = null

                    logger.debug("Subscription caught up, signalling")
                    handler.subscriptionCaughtUp()
                }
                is EventSubscriptionUpdate.Event -> {
                    if (events == null) {
                        events = mutableListOf()
                    }
                    events.add(update.event)
                }
            }
        }

        processEventsIfAny(events)
        return true
    }

    private fun processEventsIfAny(events: List<EventRecord<T>>?) {
        if (events != null && !events.isEmpty()) {
            handler.processEvents(events)
        }
    }

    private suspend fun getUpdateBatch(
            updatesChannel: ReceiveChannel<EventSubscriptionUpdate<T>>
    ): List<EventSubscriptionUpdate<T>>? {
        try {
            val results = mutableListOf<EventSubscriptionUpdate<T>>()

            // We need at least one update to do anything, this is non blocking and cancellable
            results.add(updatesChannel.receive())

            // Now that we have one, grab as many more as we can eagerly
            while (true) {
                yield() // See if we've been cancelled
                val nextUpdate = updatesChannel.poll()
                if (nextUpdate != null) {
                    results.add(nextUpdate)
                    continue
                }

                return results
            }
        } catch (ex: ClosedReceiveChannelException) {
            logger.debug("Repository closed channel, end of the line")
            return null
        }
    }

    private suspend fun sleepForRetry(attempts: Int) {
        val delayMillis = getCurrentRetryInterval(attempts)
        logger.info("Sleeping for {} millis before retrying subscription", delayMillis)
        delay(delayMillis)
    }

    private fun getCurrentRetryInterval(attempts: Int): Long {
        // This would be a simple shift, but shift would overflow ...
        var delay = config.initialRetryDelayMillis.toLong()
        for (i in 0 until attempts) {
            delay = delay shl 1
            if (delay > config.maxRetryDelayMillis) {
                return config.maxRetryDelayMillis.toLong()
            }
        }
        return delay
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionWorker::class.java)
    }
}
