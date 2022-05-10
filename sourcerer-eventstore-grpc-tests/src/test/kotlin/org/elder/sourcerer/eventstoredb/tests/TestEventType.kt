package org.elder.sourcerer.eventstoredb.tests

import org.elder.sourcerer.EventType

/**
 * Event type used for integration tests.
 */
@EventType(repositoryName = "testrepo")
data class TestEventType(
        val value: String
)
