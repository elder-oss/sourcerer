package org.elder.sourcerer2.subscriptions

data class SubscriptionNotification(
        val repositoryId: String,
        val shardValue: Int
)
