package org.elder.sourcerer.eventstoredb

import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.eventstore.tests.EventSubscriptionIntegrationTestBase

class GrpcEventSubscriptionIntegrationTest : EventSubscriptionIntegrationTestBase() {
    override fun createRepositoryFactory(sessionId: String): EventRepositoryFactory {
        return createGrpcRepositoryFactory(sessionId)
    }
}