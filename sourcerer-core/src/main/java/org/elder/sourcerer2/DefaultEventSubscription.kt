package org.elder.sourcerer2

import kotlinx.coroutines.CoroutineDispatcher
import org.elder.sourcerer2.subscription.EventSubscriptionManager
import java.lang.IllegalStateException

class DefaultEventSubscription<T>(
        private val dispatcher: CoroutineDispatcher,
        private val repository: EventRepository<T>,
        private val shard: RepositoryShard?,
        private val subscriptionHandler: EventSubscriptionHandler<T>,
        private val config: SubscriptionWorkerConfig
) : EventSubscription {
    private var positionSource: EventSubscriptionPositionSource? = null

    override fun setPositionSource(positionSource: EventSubscriptionPositionSource) {
        this.positionSource = positionSource
    }

    override fun start(): SubscriptionToken {
        val actualSubscriptionPositionSource = positionSource
                ?: throw IllegalStateException(
                        "A subscription position source is required to start a subscription")

        val subscriptionManager = EventSubscriptionManager(
                dispatcher,
                repository,
                shard,
                actualSubscriptionPositionSource,
                subscriptionHandler,
                config)
        return subscriptionManager.start()
    }
}
