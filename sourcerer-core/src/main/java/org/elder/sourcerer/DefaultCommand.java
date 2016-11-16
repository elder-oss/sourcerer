package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.exceptions.ConflictingExpectedVersionsException;
import org.elder.sourcerer.exceptions.InvalidCommandException;
import org.elder.sourcerer.exceptions.UnexpectedVersionException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default Command implementation, expressed in terms of an AggregateRepository.
 */
public class DefaultCommand<TState, TParams, TEvent> implements Command<TState, TParams, TEvent> {
    private static final Logger logger = LoggerFactory.getLogger(DefaultCommand.class);
    private final AggregateRepository<TState, TEvent> repository;
    private final Map<String, String> metadata;
    private final Operation<TState, TParams, TEvent> operation;
    private boolean atomic = true;
    private boolean idempotentCreate = false;
    private String aggregateId = null;
    private TParams arguments = null;
    private ExpectedVersion expectedVersion = null;
    private List<MetadataDecorator> metadataDecorators;

    public DefaultCommand(
            @NotNull final AggregateRepository<TState, TEvent> repository,
            @NotNull final Operation<TState, TParams, TEvent> operation) {
        Preconditions.checkNotNull(repository);
        Preconditions.checkNotNull(operation);
        this.repository = repository;
        this.operation = operation;
        this.atomic = operation.atomic();
        this.metadata = new HashMap<>();
        this.metadataDecorators = new ArrayList<>();
    }

    @Override
    public Command<TState, TParams, TEvent> setAggregateId(final String aggregateId) {
        this.aggregateId = aggregateId;
        return this;
    }

    @Override
    public Command<TState, TParams, TEvent> setArguments(final TParams arguments) {
        this.arguments = arguments;
        return this;
    }

    @Override
    public Command<TState, TParams, TEvent> setExpectedVersion(final ExpectedVersion version) {
        this.expectedVersion = version;
        return this;
    }

    @Override
    public Command<TState, TParams, TEvent> setAtomic(final boolean atomic) {
        this.atomic = atomic;
        return this;
    }

    @Override
    public Command<TState, TParams, TEvent> setIdempotentCreate(final boolean idempotentCreate) {
        this.idempotentCreate = idempotentCreate;
        this.atomic = true;
        return this;
    }

    @Override
    public Command<TState, TParams, TEvent> addMetadata(final Map<String, String> metadata) {
        this.metadata.putAll(metadata);
        return this;
    }

    @Override
    public Command<TState, TParams, TEvent> addMetadataDecorator(
            final MetadataDecorator metadataDecorator) {
        this.metadataDecorators.add(metadataDecorator);
        return this;
    }

    public Command<TState, TParams, TEvent> validate() {
        if (aggregateId == null) {
            throw new InvalidCommandException("No aggregate id specified");
        }
        if (operation.requiresArguments() && arguments == null) {
            throw new InvalidCommandException(
                    "No arguments specified to command that requires arguments");
        }

        // Try to calculate effective expected version - will validate combinations
        getEffectiveExpectedVersion(expectedVersion, operation.expectedVersion());

        // TODO: Add more validation!
        return this;
    }

    @Override
    public CommandResult<TEvent> run() {
        logger.debug("Running command on {}", aggregateId);
        validate();

        ExpectedVersion effectiveExpectedVersion =
                getEffectiveExpectedVersion(expectedVersion, operation.expectedVersion());
        logger.debug("Expected version set as {}", effectiveExpectedVersion);

        // Read the aggregate if needed
        ImmutableAggregate<TState, TEvent> aggregate;
        if (operation.requiresState() || atomic) {
            logger.debug("Reading aggregate record from stream");
            aggregate = readAndValidateAggregate(effectiveExpectedVersion);
            logger.debug(
                    "Current state of aggregate is {}",
                    aggregate.sourceVersion() == Aggregate.VERSION_NOT_CREATED
                            ? "<not created>"
                            : "version " + aggregate.sourceVersion());
        } else {
            logger.debug("Aggregate state not loaded");
            aggregate = null;
        }

        // Bail out early if idempotent create, and already present
        if (idempotentCreate
                && aggregate != null
                && aggregate.sourceVersion() != Aggregate.VERSION_NOT_CREATED) {
            logger.debug("Bailing out early as already created (and idempotent create set)");
            return new CommandResult<>(
                    aggregateId,
                    aggregate.sourceVersion(),
                    aggregate.sourceVersion(),
                    ImmutableList.of());
        }

        // Execute the command handler
        List<? extends TEvent> operationEvents = operation.execute(aggregate, arguments);
        ImmutableList<? extends TEvent> events =
                ImmutableList.copyOf(operationEvents.stream().iterator());

        if (events.isEmpty()) {
            logger.debug("Operation is no-op, bailing early");
            return new CommandResult<>(
                    aggregateId,
                    aggregate != null ? aggregate.sourceVersion() : null,
                    aggregate != null ? aggregate.sourceVersion() : null,
                    events);
        }

        // Create/append the event stream as needed
        ExpectedVersion updateExpectedVersion;

        if (atomic) {
            // Actually null safe since atomic above ...
            if (aggregate.sourceVersion() != Aggregate.VERSION_NOT_CREATED) {
                updateExpectedVersion = ExpectedVersion.exactly(aggregate.sourceVersion());
            } else {
                updateExpectedVersion = ExpectedVersion.notCreated();
            }
        } else if (idempotentCreate) {
            updateExpectedVersion = ExpectedVersion.notCreated();
        } else {
            updateExpectedVersion = ExpectedVersion.any();
        }

        // TODO: Handle any existing condition in event store - for now we know it's existing if
        // it was existing
        if (updateExpectedVersion.getType() == ExpectedVersionType.ANY_EXISTING) {
            updateExpectedVersion = ExpectedVersion.any();
        }

        logger.debug("About to persist, expected version at save: {}", updateExpectedVersion);

        Map<String, String> metadata = new HashMap<>(this.metadata);
        for (MetadataDecorator metadataDecorator : metadataDecorators) {
            Map<String, String> decoratorMetadata = metadataDecorator.getMetadata();
            if (decoratorMetadata != null) {
                metadata.putAll(decoratorMetadata);
            }
        }

        try {
            int newVersion =
                    repository.append(aggregateId, events, updateExpectedVersion, metadata);
            // It may be nice to sanity check here by using the expected version explicitly, but
            // this works regardless of whether we have a specific expected version ...
            // Will return -1 if we just created the stream, which is fine
            int oldVersion = newVersion - events.size();
            logger.debug("Save successful, new version is {}", newVersion);
            return new CommandResult<>(aggregateId, oldVersion, newVersion, events);
        } catch (UnexpectedVersionException ex) {
            // There's one case when this is OK - idempotent creates. We want to be able to create
            // a stream and not fail if the same stream is attempted to be created on replays.
            if (idempotentCreate) {
                logger.debug("Idempotent create enabled, ignoring existing stream");
                return new CommandResult<>(
                        aggregateId,
                        ex.getCurrentVersion() != null ? ex.getCurrentVersion() : null,
                        ex.getCurrentVersion() != null ? ex.getCurrentVersion() : null,
                        ImmutableList.of());
            }

            throw ex;
        }
    }

    private static ExpectedVersion getEffectiveExpectedVersion(
            final ExpectedVersion commandExpectedVersion,
            final ExpectedVersion operationExpectedVersion) {
        try {
            return ExpectedVersion.merge(commandExpectedVersion, operationExpectedVersion);
        } catch (ConflictingExpectedVersionsException ex) {
            throw new InvalidCommandException("Conflicting expected version constraints: "
                                                      + ex.getMessage(), ex);
        }
    }

    @NotNull
    private ImmutableAggregate<TState, TEvent> readAndValidateAggregate(
            final ExpectedVersion effectiveExpectedVersion) {
        ImmutableAggregate<TState, TEvent> aggregate = repository.load(aggregateId);

        // Validate expected version early if we have state
        switch (effectiveExpectedVersion.getType()) {
            case ANY:
                break;
            case ANY_EXISTING:
                if (aggregate.sourceVersion() == Aggregate.VERSION_NOT_CREATED) {
                    throw new UnexpectedVersionException(
                            aggregate.sourceVersion(),
                            effectiveExpectedVersion);
                }
                break;
            case EXACTLY:
                if (aggregate.sourceVersion() != effectiveExpectedVersion.getExpectedVersion()) {
                    throw new UnexpectedVersionException(
                            aggregate.sourceVersion(),
                            effectiveExpectedVersion);
                }
                break;
            case NOT_CREATED:
                if (aggregate.sourceVersion() != Aggregate.VERSION_NOT_CREATED
                        && !idempotentCreate) {
                    throw new UnexpectedVersionException(
                            aggregate.sourceVersion(),
                            effectiveExpectedVersion);
                }
                break;
            default:
                throw new IllegalArgumentException(
                        "Unrecognized expected version type " + effectiveExpectedVersion.getType());
        }
        return aggregate;
    }
}
