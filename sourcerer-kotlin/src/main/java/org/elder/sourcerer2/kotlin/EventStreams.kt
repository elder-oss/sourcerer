package org.elder.sourcerer2.kotlin

import org.elder.sourcerer2.AggregateRepository
import org.elder.sourcerer2.CommandFactory
import org.elder.sourcerer2.CommandResponse
import org.elder.sourcerer2.DefaultCommandFactory
import org.elder.sourcerer2.ExpectedVersion
import org.elder.sourcerer2.ImmutableAggregate
import org.elder.sourcerer2.OperationHandler
import org.elder.sourcerer2.OperationHandlerOperation
import org.elder.sourcerer2.Operations
import org.elder.sourcerer2.functions.UpdateHandlerAggregate

/**
 * Allows for creating, updating or appending to eventstore streams.
 *
 * Kotlin wrapper for CommandFactory that simplifies its usage.
 */
class EventStreams<STATE, EVENT>(
        private val commandFactory: CommandFactory<STATE, EVENT>
) {

    /**
     * Create EventStreams that uses a DefaultCommandFactory.
     */
    constructor(
            aggregateRepository: AggregateRepository<STATE, EVENT>
    ) : this(DefaultCommandFactory(aggregateRepository))

    /**
     * Create a new stream.
     *
     * Will fail if stream already exists.
     */
    fun create(
            id: String,
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
                        .run())
    }

    /**
     * Update existing stream.
     *
     * Will by default fail if stream does not exist.
     */
    fun update(
            id: String,
            expectedVersion: ExpectedVersion = ExpectedVersion.anyExisting(),
            update: (ImmutableAggregate<STATE, EVENT>) -> ImmutableAggregate<STATE, EVENT>
    ): CommandResponse {
        return CommandResponse.of(
                commandFactory
                        .fromOperation(updateOf(update, expectedVersion))
                        .setAggregateId(id)
                        .setAtomic(true)
                        .setExpectedVersion(expectedVersion)
                        .run())
    }

    /**
     * Append events to stream.
     *
     * Stream may be new or existing. No validation is performed, and nothing is read.
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
