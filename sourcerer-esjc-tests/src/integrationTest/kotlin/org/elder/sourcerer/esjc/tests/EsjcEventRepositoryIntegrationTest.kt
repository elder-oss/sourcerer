package org.elder.sourcerer.esjc.tests

import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.eventstore.tests.EventRepositoryIntegrationTestBase

class EsjcEventRepositoryIntegrationTest
    : EventRepositoryIntegrationTestBase() {
    override fun createRepositoryFactory(sessionId: String): EventRepositoryFactory {
        return createEsjcRepositoryFactory(sessionId)
    }
}
