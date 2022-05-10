package org.elder.sourcerer.esjc.tests

import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.eventstore.tests.EventStreamsIntegrationTestBase

class EsjcEventStreamsIntegrationTest : EventStreamsIntegrationTestBase() {
    override fun createRepositoryFactory(sessionId: String): EventRepositoryFactory {
        return createEsjcRepositoryFactory(sessionId)
    }
}