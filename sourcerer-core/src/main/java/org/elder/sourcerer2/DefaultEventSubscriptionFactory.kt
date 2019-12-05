package org.elder.sourcerer2

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DefaultEventSubscriptionFactory<T> @JvmOverloads constructor(
        private val repository: EventRepository<T>,
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : EventSubscriptionFactory<T> {
    override fun fromSubscriptionHandler(
            handler: EventSubscriptionHandler<T>,
            config: SubscriptionWorkerConfig,
            shard: RepositoryShard?
    ): EventSubscription {
        return DefaultEventSubscription(dispatcher, repository, shard, handler, config)
    }
}
