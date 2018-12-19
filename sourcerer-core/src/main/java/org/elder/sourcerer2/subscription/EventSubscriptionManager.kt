package org.elder.sourcerer2.subscription

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.elder.sourcerer2.EventRepository
import org.elder.sourcerer2.EventSubscriptionHandler
import org.elder.sourcerer2.EventSubscriptionPositionSource
import org.elder.sourcerer2.SubscriptionToken
import org.elder.sourcerer2.SubscriptionWorkerConfig
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

/**
 * Custom subscriber to work around the fact that buffer does not appear to respect back pressure,
 * in RxJava or Project Reactor! This has the added benefit of not requiring time windows, but
 * rather process updates in batch automatically if the subscriber is not fast enough to keep up
 * with them sent one by one - i.e. normally batch in replay model, and one by one delivery when
 * live - but without added latency in the live case.
 *
 *
 * This implementation uses a dedicated worker thread per subscription, which is expensive but easy
 * to manage.
 *
 * @param <T> The type of events the subscription handles.
 */
class EventSubscriptionManager<T>(
        private val dispatcher: CoroutineDispatcher,
        repository: EventRepository<T>,
        shard: Int?,
        positionSource: EventSubscriptionPositionSource,
        private val subscriptionHandler: EventSubscriptionHandler<T>,
        config: SubscriptionWorkerConfig
) : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = dispatcher

    private val subscriptionWorker = SubscriptionWorker(
            repository,
            shard,
            positionSource,
            subscriptionHandler,
            config
    )

    fun start(): SubscriptionToken {
        lateinit var token: JobSubscriptionToken
        val job = launch(start = CoroutineStart.LAZY) {
            try {
                subscriptionHandler.subscriptionStarted(token)
                subscriptionWorker.run()
                logger.info("Subscription has reached the end")
                subscriptionHandler.subscriptionStopped()
            } catch (ex: CancellationException) {
                logger.info("Subscription worker cancelled, clean shutdown")
                subscriptionHandler.subscriptionStopped()
            } catch (ex: Throwable) {
                logger.error("Subscription failed with terminal error, we're bailing out", ex)
                subscriptionHandler.subscriptionFailed(ex)
            }
        }

        token = JobSubscriptionToken(job)
        job.start()
        return token
    }

    class JobSubscriptionToken(private val job: Job) : SubscriptionToken {
        override fun stop() {
            logger.info("Cancelling subscription after explicit request to do so")
            job.cancel()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventSubscriptionManager::class.java)
    }
}
