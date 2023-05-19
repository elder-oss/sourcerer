package org.elder.sourcerer.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import org.elder.sourcerer.EventData
import org.elder.sourcerer.EventRepository
import org.elder.sourcerer.ExpectedVersion
import org.elder.sourcerer.Snapshot
import org.elder.sourcerer.kotlin.SnapshotEvent.Companion.SNAPSHOT_ADDED_EVENT_NAME
import org.slf4j.LoggerFactory
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
        try {
            val snapshotVersion = snapshot?.streamVersion ?: -1
            val snapshotIsWellBehind = newVersion - snapshotVersion >= minVersionsBetweenSnapshots
            if (snapshotIsWellBehind) {
                val snapshotEntityId = snapshotEntityId(id)
                val snapshot = Snapshot(newState, newVersion)
                snapshotAppend(snapshotEntityId) {
                    listOf(SnapshotEvent.Added(snapshot, snapshottingVersion))
                }
            }
        } catch (ex: Exception) {
            logger.warn("Error attempting to create new snapshot", ex)
        }
    }

    fun findSnapshot(
        entityId: String
    ) : Snapshot<STATE>? {
        return try {
            val snapshotEntityId = snapshotEntityId(entityId)
            snapshotRepository.readLast(snapshotEntityId)
                ?.event
                ?.let { it as SnapshotEvent.Added<STATE> }
                ?.takeIf { it.monitorVersion == snapshottingVersion }
                ?.snapshot
                ?.parse()
        } catch (ex: Exception) {
            logger.warn("Error trying to find snapshot", ex)
            null
        }
    }

    private fun Snapshot<STATE>.parse() : Snapshot<STATE> {
        val state = mapper.convertValue(state, clazz) as STATE
        return Snapshot<STATE>(state, streamVersion)
    }

    private fun snapshotAppend(id: String, operation: () -> List<SnapshotEvent>) {
        val events = operation().map { event ->
            EventData(
                SNAPSHOT_ADDED_EVENT_NAME,
                UUID.randomUUID(),
                mapOf(),
                event)
        }
        snapshotRepository.append(id, events, ExpectedVersion.any())
    }

    private fun snapshotEntityId(entityId: String) = "$entityId-$snapshottingVersion"

    companion object {
        private val logger = LoggerFactory.getLogger(SnapshottingSupport::class.java)
    }
}