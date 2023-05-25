package org.elder.sourcerer.kotlin

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import org.elder.sourcerer.EventData
import org.elder.sourcerer.EventRepository
import org.elder.sourcerer.ExpectedVersion
import org.elder.sourcerer.Snapshot
import org.elder.sourcerer.kotlin.SnapshotEvent.Companion.SNAPSHOT_ADDED_EVENT_NAME
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class SnapshottingSupport<STATE>(
    val snapshottingEnabled: Boolean = false,
    val compressingEnabled: Boolean = false,
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
                    listOf(
                        when (compressingEnabled) {
                            true -> SnapshotEvent.CompressedAdded(snapshot.compress(), snapshottingVersion)
                            false -> SnapshotEvent.Added(snapshot, snapshottingVersion)
                        })
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
            val event = snapshotRepository.readLast(snapshotEntityId)
                ?.event
                ?.takeIf { it.monitorVersion == snapshottingVersion }
            when (event) {
                is SnapshotEvent.Added<*>-> event.snapshot.parse()
                is SnapshotEvent.CompressedAdded -> event.compressedSnapshot.uncompress()
                else -> null
            }
        } catch (ex: Exception) {
            logger.warn("Error trying to find snapshot", ex)
            null
        }
    }

    private fun Snapshot<STATE>.compress() : Snapshot<String> {
        val data = mapper.writeValueAsBytes(state)
        logger.info("REMOVEME: Size before compressing: ${data.size}")
        val compressed = ByteArrayOutputStream().use {
            GZIPOutputStream(it).use { it.write(data) }
            it
        }.toByteArray()
        logger.info("REMOVEME: Size after compressing: ${compressed.size}")
        val stringState = Base64.getEncoder().encodeToString(compressed)
        logger.info("REMOVEME: Array length: ${stringState.length}")
        return Snapshot<String>(stringState, streamVersion)
    }

    private fun Snapshot<String>.uncompress() : Snapshot<STATE> {
        val data = Base64.getDecoder().decode(state)
        val uncompressed = GZIPInputStream(data.inputStream()).use { it.readBytes() }
        val state = mapper.readValue(uncompressed, clazz) as STATE
        return Snapshot<STATE>(state, streamVersion)
    }

    private fun Snapshot<*>.parse() : Snapshot<STATE> {
        val state = mapper.convertValue(state, clazz) as STATE
        return Snapshot<STATE>(state, streamVersion)
    }

    private fun snapshotAppend(id: String, operation: () -> List<SnapshotEvent>) {
        val events = operation().map { event ->
            val eventTypeName = event::class.java.annotations
                .first { it.annotationClass == JsonTypeName::class }
                .let { it as JsonTypeName }
                .value
            EventData(
                eventTypeName,
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