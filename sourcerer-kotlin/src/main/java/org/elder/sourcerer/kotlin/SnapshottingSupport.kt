package org.elder.sourcerer.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import org.elder.sourcerer.CommandFactory
import org.elder.sourcerer.CommandResponse
import org.elder.sourcerer.EventRepository
import org.elder.sourcerer.Operations
import org.elder.sourcerer.Snapshot

class SnapshottingSupport<STATE>(
    val snapshottingEnabled: Boolean = false,
    private val snapshotRepository: EventRepository<SnapshotEvent>,
    private val snapshotCommandFactory: CommandFactory<STATE, SnapshotEvent>,
    private val clazz: Class<STATE>,
    private val mapper: ObjectMapper,
    private val snapshottingVersion: String,
    private val minVersionsBetweenSnapshots: Int
) {

    fun <STATE> createNewSnapshotIfNecessary(
        id: String,
        snapshot: Snapshot<STATE>?,
        newVersion: Int,
        newState: STATE
    ) {
        val minVersionsBetweenSnapshots = minVersionsBetweenSnapshots
        val snapshottingVersion = snapshottingVersion
        val snapshotIsWellBehind = snapshot == null
                || (newVersion - snapshot.streamVersion) > minVersionsBetweenSnapshots
        if (snapshotIsWellBehind) {
            val snapshotEntityId = snapshotEntityId(id)
            val snapshot = Snapshot(newState, newVersion)
            snapshotAppend(snapshotEntityId) {
                listOf(SnapshotEvent.Added(snapshot, snapshottingVersion))
            }
        }
    }

    fun <STATE> findSnapshot(
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

    private fun <STATE> Snapshot<STATE>.parse() : Snapshot<STATE> {
        val state = mapper.convertValue(state, clazz) as STATE
        return Snapshot<STATE>(state, streamVersion)
    }

    private fun snapshotAppend(id: String, operation: () -> List<SnapshotEvent>): CommandResponse {
        return CommandResponse.of(
            snapshotCommandFactory!!
                .fromOperation(Operations.appendOf(operation))
                .setAggregateId(id)
                .run())
    }

    private fun snapshotEntityId(entityId: String) = "$entityId-$snapshottingVersion"
}