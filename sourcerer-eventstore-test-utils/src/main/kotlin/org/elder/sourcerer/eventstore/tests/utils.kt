package org.elder.sourcerer.eventstore.tests

import java.util.UUID

fun randomSessionId(): String {
    return UUID.randomUUID().toString().replace("-", "")
}
