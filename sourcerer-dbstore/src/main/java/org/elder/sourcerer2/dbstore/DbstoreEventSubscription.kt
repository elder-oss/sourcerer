package org.elder.sourcerer2.dbstore

import java.nio.channels.Channel

class DbstoreEventSubscription<T>(
        private val reader: () -> List<DbstoreEventRow>
) {
    private var position: DbstoreRepositoryVersion? = null
    private val triggerCheck = Channel<DbstoreRepositoryVersion?>
    private var hasAdvertisedCaughtUp = false

    fun suspend run() {
        val channel

    }

}