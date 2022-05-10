package org.elder.sourcerer.eventstoredb

import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.eventstore.tests.EventRepositoryIntegrationTestBase

class GrpcEventRepositoryIntegrationTest : EventRepositoryIntegrationTestBase() {
    override fun createRepositoryFactory(sessionId: String): EventRepositoryFactory {
        return createGrpcRepositoryFactory(sessionId)
    }
}