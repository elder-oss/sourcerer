package org.elder.sourcerer2

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Identifier for a particular individual stream of events. This id is normally unique only within
 * the repository that is being used to read or write it, e.g. for the events relating to
 * aggregates of the same type.
 */
data class StreamId @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
        @get:JsonValue
        val identifier: String
) {
    companion object {
        @JvmStatic
        fun ofString(identifier: String): StreamId {
            return StreamId(identifier)
        }
    }
}
