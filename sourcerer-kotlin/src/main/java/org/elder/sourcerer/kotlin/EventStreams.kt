package org.elder.sourcerer.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import org.elder.sourcerer.AggregateRepository
import org.elder.sourcerer.CommandFactory
import org.elder.sourcerer.CommandResponse
import org.elder.sourcerer.DefaultCommandFactory
import org.elder.sourcerer.EventRepository
import org.elder.sourcerer.ExpectedVersion
import org.elder.sourcerer.ImmutableAggregate
import org.elder.sourcerer.OperationHandler
import org.elder.sourcerer.OperationHandlerOperation
import org.elder.sourcerer.Operations
import org.elder.sourcerer.Snapshot
import org.elder.sourcerer.functions.UpdateHandlerAggregate

/**
 * Allows for creating, updating or appending to eventstore streams.
 *
 * Kotlin wrapper for CommandFactory that simplifies its usage.
 */
class EventStreams<STATE, EVENT>(
        private val commandFactory: CommandFactory<STATE, EVENT>,
        private val snapshottingEnabled: Boolean = false,
        private val snapshotRepository: EventRepository<SnapshotEvent>? = null,
        private val snapshotCommandFactory: CommandFactory<STATE, SnapshotEvent>? = null,
        private val clazz: Class<STATE>? = null,
        private val mapper: ObjectMapper? = null,
        private val snapshottingVersion: String? = null,
        private val minVersionsBetweenSnapshots: Int? = null
) {

    /**
     * Create EventStreams that uses a DefaultCommandFactory.
     */
    constructor(
            aggregateRepository: AggregateRepository<STATE, EVENT>
    ) : this(DefaultCommandFactory(aggregateRepository))

    /**
     * For backwards compatibility. Create a EventStreams with no snapshotting.
     */
    constructor(
        commandFactory: CommandFactory<STATE, EVENT>
    ) : this(commandFactory, false)


    /**
     * Create a new stream.
     *
     * @param id the aggregate id
     * @param conflictStrategy what to do if aggregate already exists. Default is to fail
     * @param create operations to perform on the aggregate
     */
    fun create(
            id: String,
            conflictStrategy: CreateConflictStrategy = CreateConflictStrategy.FAIL,
            create: (ImmutableAggregate<STATE, EVENT>) -> ImmutableAggregate<STATE, EVENT>
    ): CommandResponse {
        return CommandResponse.of(
                commandFactory
                        .fromOperation(constructOf(UpdateHandlerAggregate { state ->
                            create(state)
                        }))
                        .setAggregateId(id)
                        .setAtomic(true)
                        .setExpectedVersion(ExpectedVersion.notCreated())
                        .setIdempotentCreate(conflictStrategy.idempotentCreate)
                        .run())
    }

    /**
     * Update existing stream.
     *
     * @param id the aggregate id
     * @param expectedVersion expected aggregate version. By default will fail if stream does not
     * exist
     * @param update operations to perform on the aggregate
     */
    fun update(
            id: String,
            expectedVersion: ExpectedVersion = ExpectedVersion.anyExisting(),
            update: (ImmutableAggregate<STATE, EVENT>) -> ImmutableAggregate<STATE, EVENT>
    ): CommandResponse {
        return when (snapshottingEnabled) {
            true -> {
                val snapshot = findSnapshot<STATE>(id)
                return doUpdate(id, expectedVersion, snapshot) { agg ->
                    val updatedAggregate = update(agg)
                    createNewSnapshotIfNecessary(
                        id = id,
                        snapshot = snapshot,
                        newVersion = updatedAggregate.sourceVersion(),
                        newState = updatedAggregate.state())
                    updatedAggregate
                }
            }
            else -> doUpdate(id, expectedVersion) { agg -> update(agg) }
        }
    }

    private fun doUpdate(
        id: String,
        expectedVersion: ExpectedVersion = ExpectedVersion.anyExisting(),
        snapshot: Snapshot<STATE>? = null,
        update: (ImmutableAggregate<STATE, EVENT>) -> ImmutableAggregate<STATE, EVENT>
    ): CommandResponse {
        return CommandResponse.of(
            commandFactory
                .fromOperation(updateOf(update, expectedVersion))
                .setAggregateId(id)
                .setAtomic(true)
                .setExpectedVersion(expectedVersion)
                .setSnapshot(snapshot)
                .run())
    }

    private fun <STATE> findSnapshot(
        entityId: String
    ) : Snapshot<STATE>? {
        val snapshotEntityId = snapshotEntityId(entityId)
        return snapshotRepository!!.readLast(snapshotEntityId)
            ?.event
            ?.let { it as SnapshotEvent.Added<STATE> }
            ?.takeIf { it.monitorVersion == snapshottingVersion }
            ?.snapshot
            ?.parse()
    }

    private fun <STATE> createNewSnapshotIfNecessary(
        id: String,
        snapshot: Snapshot<STATE>?,
        newVersion: Int,
        newState: STATE
    ) {
        val snapshotIsWellBehind = snapshot == null
                || (newVersion - snapshot.streamVersion) > minVersionsBetweenSnapshots!!
        if (snapshotIsWellBehind) {
            val snapshotEntityId = snapshotEntityId(id)
            val snapshot = Snapshot(newState, newVersion)
            snapshotAppend(snapshotEntityId) {
                listOf(SnapshotEvent.Added(snapshot, snapshottingVersion!!))
            }
        }
    }

    private fun <STATE> Snapshot<STATE>.parse() : Snapshot<STATE> {
        val state = mapper!!.convertValue(state, clazz) as STATE
        return Snapshot<STATE>(state, streamVersion)
    }

    private fun snapshotEntityId(entityId: String) = "$entityId-$snapshottingVersion"

    /**
     * Append events to stream.
     *
     * Stream may be new or existing. No validation is performed, and nothing is read.
     *
     * @param id the aggregate id
     * @param operation operations to perform to create events
     */
    fun append(id: String, operation: () -> List<EVENT>): CommandResponse {
        return CommandResponse.of(
                commandFactory
                        .fromOperation(Operations.appendOf(operation))
                        .setAggregateId(id)
                        .run())
    }

    private fun snapshotAppend(id: String, operation: () -> List<SnapshotEvent>): CommandResponse {
        return CommandResponse.of(
            snapshotCommandFactory!!
                .fromOperation(Operations.appendOf(operation))
                .setAggregateId(id)
                .run())
    }

    private fun updateOf(
            update: (ImmutableAggregate<STATE, EVENT>) -> ImmutableAggregate<STATE, EVENT>,
            expectedVersion: ExpectedVersion
    ): OperationHandlerOperation<STATE, Any, EVENT> {
        return OperationHandlerOperation(
                OperationHandler { agg, _ -> update(agg!!).events() },
                true,
                false,
                expectedVersion,
                true)
    }

    private fun constructOf(
            operation: UpdateHandlerAggregate<STATE, EVENT>
    ): OperationHandlerOperation<STATE, Any, EVENT> {
        return OperationHandlerOperation(
                operation,
                true,
                false,
                ExpectedVersion.notCreated(),
                true)
    }
}

/**
 * Strategy for when trying to create an aggregate that already exists.
 */
enum class CreateConflictStrategy(internal val idempotentCreate: Boolean) {
    /**
     * Throw UnexpectedVersionException.
     */
    FAIL(idempotentCreate = false),
    /**
     * Do nothing.
     */
    NOOP(idempotentCreate = true);
}
