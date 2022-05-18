package org.elder.sourcerer.eventstoredb

import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.eventstore.tests.EventStreamsIntegrationTestBase

class GrpcEventStreamsIntegrationTest : EventStreamsIntegrationTestBase() {
    override fun createRepositoryFactory(sessionId: String): EventRepositoryFactory {
        return createGrpcRepositoryFactory(sessionId)
    }
}