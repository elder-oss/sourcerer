package org.elder.sourcerer.kotlin

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.elder.sourcerer.EventType
import org.elder.sourcerer.Snapshot

@EventType
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(SnapshotEvent.Added::class)
)
sealed class SnapshotEvent {

    @JsonTypeName(SNAPSHOT_ADDED_EVENT_NAME)
    data class Added<State>(
        val snapshot: Snapshot<State>,
        val monitorVersion: String
    ) : SnapshotEvent()

    companion object {
        const val SNAPSHOT_ADDED_EVENT_NAME = "snapshotAdded"
    }
}

