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
    JsonSubTypes.Type(SnapshotEvent.Added::class),
    JsonSubTypes.Type(SnapshotEvent.CompressedAdded::class)
)
sealed class SnapshotEvent {

    abstract val monitorVersion: String

    @JsonTypeName(SNAPSHOT_ADDED_EVENT_NAME)
    data class Added<State>(
        val snapshot: Snapshot<State>,
        override val monitorVersion: String
    ) : SnapshotEvent()

    @JsonTypeName(COMPRESSED_SNAPSHOT_ADDED_EVENT_NAME)
    data class CompressedAdded(
        val compressedSnapshot: Snapshot<String>,
        override val monitorVersion: String
    ) : SnapshotEvent()

    companion object {
        const val SNAPSHOT_ADDED_EVENT_NAME = "snapshotAdded"
        const val COMPRESSED_SNAPSHOT_ADDED_EVENT_NAME = "compressedSnapshotAdded"
    }
}

