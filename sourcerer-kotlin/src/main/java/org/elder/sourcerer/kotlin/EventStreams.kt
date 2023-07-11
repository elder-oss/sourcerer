package org.elder.sourcerer.kotlin

import org.elder.sourcerer.AggregateRepository
import org.elder.sourcerer.CommandFactory
import org.elder.sourcerer.CommandResponse
import org.elder.sourcerer.DefaultCommandFactory
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
        private val snapshottingSupport: SnapshottingSupport<STATE>? = null
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
    ) : this(commandFactory, null)


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
        return when (snapshottingSupport?.snapshottingEnabled) {
            true -> {
                val snapshot = snapshottingSupport.findSnapshot(id)
                lateinit var newState : STATE
                val commandResponse = doUpdate(id, expectedVersion, snapshot) { agg ->
                    val updatedAggregate = update(agg)
                    newState = updatedAggregate.state()
                    updatedAggregate
                }
                snapshottingSupport.createNewSnapshotIfNecessary(
                    id = id,
                    snapshot = snapshot,
                    newVersion = commandResponse.newVersion,
                    newState = newState!!)
                commandResponse
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
