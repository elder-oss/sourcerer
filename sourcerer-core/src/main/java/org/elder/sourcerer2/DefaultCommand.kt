package org.elder.sourcerer2

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import org.elder.sourcerer2.exceptions.AtomicWriteException
import org.elder.sourcerer2.exceptions.ConflictingExpectedVersionsException
import org.elder.sourcerer2.exceptions.InvalidCommandException
import org.elder.sourcerer2.exceptions.UnexpectedVersionException
import org.elder.sourcerer2.utils.RetryHandler
import org.elder.sourcerer2.utils.RetryPolicy
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.HashMap

/**
 * Default Command implementation, expressed in terms of an AggregateRepository.
 */

class DefaultCommand<TState, TParams, TEvent> @JvmOverloads constructor(
        private val repository: AggregateRepository<TState, TEvent>,
        private val operation: Operation<TState, TParams, TEvent>,
        private val retryPolicy: RetryPolicy = RetryPolicy.noRetries()
) : Command<TState, TParams, TEvent> {
    private val metadata: MutableMap<String, String>
    private var atomic = true
    private var idempotentCreate = false
    private var aggregateId: StreamId? = null
    private var arguments: TParams? = null
    private var expectedVersion: ExpectedVersion? = null
    private val metadataDecorators: MutableList<MetadataDecorator>

    init {
        Preconditions.checkNotNull(repository)
        Preconditions.checkNotNull(operation)
        this.atomic = operation.atomic()
        this.metadata = HashMap()
        this.metadataDecorators = ArrayList()
    }

    override fun setAggregateId(aggregateId: StreamId): Command<TState, TParams, TEvent> {
        this.aggregateId = aggregateId
        return this
    }

    override fun setArguments(arguments: TParams): Command<TState, TParams, TEvent> {
        this.arguments = arguments
        return this
    }

    override fun setExpectedVersion(version: ExpectedVersion): Command<TState, TParams, TEvent> {
        this.expectedVersion = version
        return this
    }

    override fun setAtomic(atomic: Boolean): Command<TState, TParams, TEvent> {
        this.atomic = atomic
        return this
    }

    override fun setIdempotentCreate(idempotentCreate: Boolean): Command<TState, TParams, TEvent> {
        this.idempotentCreate = idempotentCreate
        this.atomic = true
        return this
    }

    override fun addMetadata(metadata: Map<String, String>): Command<TState, TParams, TEvent> {
        this.metadata.putAll(metadata)
        return this
    }

    override fun addMetadataDecorator(
            metadataDecorator: MetadataDecorator): Command<TState, TParams, TEvent> {
        this.metadataDecorators.add(metadataDecorator)
        return this
    }

    override fun validate(): Command<TState, TParams, TEvent> {
        if (aggregateId == null) {
            throw InvalidCommandException("No aggregate id specified")
        }
        if (operation.requiresArguments() && arguments == null) {
            throw InvalidCommandException(
                    "No arguments specified to command that requires arguments")
        }

        // Try to calculate effective expected version - will validate combinations
        getEffectiveExpectedVersion(expectedVersion, operation.expectedVersion())

        // TODO: Add more validation!
        return this
    }

    override fun run(): CommandResult<TEvent> {
        logger.debug("Running command on {}", aggregateId);
        validate();

        val effectiveExpectedVersion = getEffectiveExpectedVersion(expectedVersion, operation.expectedVersion());
        logger.debug("Expected version set as {}", effectiveExpectedVersion);

        val retryHandler = RetryHandler(retryPolicy);
        while (true) {
            try {
                return performCommand(effectiveExpectedVersion);
            } catch (awe: AtomicWriteException) {
                if (effectiveExpectedVersion == ExpectedVersion.notCreated()) {
                    // Expected aggregate to not exist, but now it does - retrying won't help.
                    throw awe;
                }
                retryHandler.failed();
                logger.info(
                        "Failed attempt {}: Concurrent append to aggregate {}",
                        retryHandler.nrFailures,
                        aggregateId
                );
                if (retryHandler.isThresholdReached) {
                    logger.warn("Reached max retries");
                    throw awe;
                }
                retryHandler.backOff();
            }
        }
    }

    private fun performCommand(
            effectiveExpectedVersion: ExpectedVersion
    ): CommandResult<TEvent> {
        // Read the aggregate if needed
        val aggregate = readExistingAggregate(effectiveExpectedVersion)

        // Bail out early if idempotent create, and already present
        if (idempotentCreate
                && aggregate != null
                && aggregate!!.sourceVersion() !== Aggregate.VERSION_NOT_CREATED) {
            logger.debug("Bailing out early as already created (and idempotent create set)")
            return CommandResult(
                    aggregateId!!,
                    aggregate!!.sourceVersion(),
                    aggregate!!.sourceVersion(),
                    ImmutableList.of())
        }

        // Execute the command handler
        val operationEvents = operation.execute(aggregate, arguments)
        val events = ImmutableList.copyOf(operationEvents.stream().iterator())

        if (events.isEmpty()) {
            logger.debug("Operation is no-op, bailing early")
            return CommandResult(
                    aggregateId,
                    if (aggregate != null) aggregate!!.sourceVersion() else null,
                    if (aggregate != null) aggregate!!.sourceVersion() else null,
                    events)
        }

        // Create/update the event stream as needed
        return updateAggregate(aggregate, events)
    }

    private fun readExistingAggregate(
            effectiveExpectedVersion: ExpectedVersion
    ): ImmutableAggregate<TState, TEvent>? {
        val aggregate: ImmutableAggregate<TState, TEvent>
        return if (operation.requiresState() || atomic) {
            logger.debug("Reading aggregate record from stream")
            aggregate = readAndValidateAggregate(effectiveExpectedVersion)
            logger.debug(
                    "Current state of aggregate is {}",
                    if (aggregate.sourceVersion() === Aggregate.VERSION_NOT_CREATED)
                        "<not created>"
                    else
                        "version " + aggregate.sourceVersion())
            aggregate
        } else {
            logger.debug("Aggregate state not loaded")
            null
        }
    }

    private fun updateAggregate(
            aggregate: ImmutableAggregate<TState, TEvent>?,
            events: ImmutableList<out TEvent>
    ): CommandResult<TEvent> {
        var updateExpectedVersion: ExpectedVersion

        updateExpectedVersion = if (atomic) {
            // Actually null safe since atomic above ...
            if (aggregate!!.sourceVersion() !== Aggregate.VERSION_NOT_CREATED) {
                ExpectedVersion.exactly(aggregate!!.sourceVersion())
            } else {
                ExpectedVersion.notCreated()
            }
        } else if (idempotentCreate) {
            ExpectedVersion.notCreated()
        } else {
            ExpectedVersion.any()
        }

        // TODO: Handle any existing condition in event store - for now we know it's existing if
        // it was existing
        if (updateExpectedVersion == ExpectedVersion.anyExisting()) {
            updateExpectedVersion = ExpectedVersion.any()
        }

        logger.debug("About to persist, expected version at save: {}", updateExpectedVersion)

        val effectiveMetadata = HashMap(this.metadata)
        for (metadataDecorator in metadataDecorators) {
            val decoratorMetadata = metadataDecorator.metadata
            if (decoratorMetadata != null) {
                effectiveMetadata.putAll(decoratorMetadata)
            }
        }

        effectiveMetadata.putAll(this.metadata)

        try {
            val newVersion = repository.append(
                    aggregateId!!,
                    events,
                    updateExpectedVersion,
                    effectiveMetadata)

            // It may be nice to sanity check here by using the expected version explicitly, but
            // this works regardless of whether we have a specific expected version ...
            // Will return -1 if we just created the stream, which is fine
            val oldVersion = expectedVersion.let {
                when (it) {
                    is ExpectedVersion.Exactly -> it.streamVersion
                    else -> null
                }
            }
            logger.debug("Save successful, new version is {}", newVersion)
            return CommandResult(aggregateId, oldVersion, newVersion, events)
        } catch (ex: UnexpectedVersionException) {
            // There's one case when this is OK - idempotent creates. We want to be able to create
            // a stream and not fail if the same stream is attempted to be created on replays.
            if (idempotentCreate) {
                logger.debug("Idempotent create enabled, ignoring existing stream")
                return CommandResult(
                        aggregateId,
                        ex.currentVersion,
                        ex.currentVersion,
                        ImmutableList.of())
            } else if (atomic) {
                throw AtomicWriteException(ex)
            }

            throw ex
        }

    }

    private fun readAndValidateAggregate(
            effectiveExpectedVersion: ExpectedVersion
    ): ImmutableAggregate<TState, TEvent> {
        val aggregate = repository.load(aggregateId!!)

        // Validate expected version early if we have state
        when (effectiveExpectedVersion) {
            ExpectedVersion.Any -> {
            }
            ExpectedVersion.AnyExisting -> {
                if (aggregate.sourceVersion() == Aggregate.VERSION_NOT_CREATED) {
                    throw UnexpectedVersionException(
                            aggregate.sourceVersion(),
                            effectiveExpectedVersion)
                }
            }
            ExpectedVersion.NotCreated -> {
                if (aggregate.sourceVersion() != Aggregate.VERSION_NOT_CREATED && !idempotentCreate) {
                    throw UnexpectedVersionException(
                            aggregate.sourceVersion(),
                            effectiveExpectedVersion)
                }
            }
            is ExpectedVersion.Exactly -> {
                if (aggregate.sourceVersion() != effectiveExpectedVersion.streamVersion) {
                    throw UnexpectedVersionException(
                            aggregate.sourceVersion(),
                            effectiveExpectedVersion)
                }
            }
        }
        return aggregate
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultCommand::class.java)

        private fun getEffectiveExpectedVersion(
                commandExpectedVersion: ExpectedVersion?,
                operationExpectedVersion: ExpectedVersion
        ): ExpectedVersion {
            try {
                return ExpectedVersion.merge(commandExpectedVersion, operationExpectedVersion)
            } catch (ex: ConflictingExpectedVersionsException) {
                throw InvalidCommandException(
                        "Conflicting expected version constraints: " + ex.message,
                        ex)
            }
        }
    }
}
