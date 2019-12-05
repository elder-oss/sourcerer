package org.elder.sourcerer2.subscriptions

import kotlinx.coroutines.channels.ReceiveChannel
import org.elder.sourcerer2.RepositoryShard

interface SubscriptionNotificationManager {
    fun advertiseSubscription(repositoryId: String)
    fun subscribe(repositoryId: String, shard: RepositoryShard): ReceiveChannel<SubscriptionNotification>
}
