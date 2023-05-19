package org.elder.sourcerer.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import org.elder.sourcerer.EventData
import org.elder.sourcerer.EventRepository
import org.elder.sourcerer.ExpectedVersion
import org.elder.sourcerer.Snapshot
import java.util.UUID

class SnapshottingSupport<STATE>(
    val snapshottingEnabled: Boolean = false,
    private val snapshotRepository: EventRepository<SnapshotEvent>,
    private val clazz: Class<STATE>,
    private val mapper: ObjectMapper,
    private val snapshottingVersion: String,
    private val minVersionsBetweenSnapshots: Int
) {

    fun createNewSnapshotIfNecessary(
        id: String,
        snapshot: Snapshot<STATE>?,
        newVersion: Int,
        newState: STATE
    ) {
        val snapshotVersion = snapshot?.streamVersion ?: -1
        val snapshotIsWellBehind = newVersion - snapshotVersion > minVersionsBetweenSnapshots
        if (snapshotIsWellBehind) {
            val snapshotEntityId = snapshotEntityId(id)
            val snapshot = Snapshot(newState, newVersion)
            snapshotAppend(snapshotEntityId) {
                listOf(SnapshotEvent.Added(snapshot, snapshottingVersion))
            }
        }
    }

    fun findSnapshot(
        entityId: String
    ) : Snapshot<STATE>? {
        val snapshotEntityId = snapshotEntityId(entityId)
        return snapshotRepository.readLast(snapshotEntityId)
            ?.event
            ?.let { it as SnapshotEvent.Added<STATE> }
            ?.takeIf { it.monitorVersion == snapshottingVersion }
            ?.snapshot
            ?.parse()
    }

    private fun Snapshot<STATE>.parse() : Snapshot<STATE> {
        val state = mapper.convertValue(state, clazz) as STATE
        return Snapshot<STATE>(state, streamVersion)
    }

    private fun snapshotAppend(id: String, operation: () -> List<SnapshotEvent>) {
        val events = operation().map { event ->
            EventData("snapshotEvent",
                UUID.randomUUID(),
                mapOf(),
                event)
        }
        snapshotRepository.append(id, events, ExpectedVersion.any())
    }

    private fun snapshotEntityId(entityId: String) = "$entityId-$snapshottingVersion"
}