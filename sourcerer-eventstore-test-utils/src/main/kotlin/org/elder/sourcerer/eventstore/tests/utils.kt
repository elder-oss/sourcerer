package org.elder.sourcerer.eventstore.tests

import org.elder.sourcerer.EventData
import java.util.UUID

internal fun randomSessionId(): String {
    return UUID.randomUUID().toString().replace("-", "")
}

internal fun <T> eventData(event: T): EventData<T> {
    return EventData(
            "eventType",
            UUID.randomUUID(),
            mapOf(),
            event
    )
}
