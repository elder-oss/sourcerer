package org.elder.sourcerer2.esjc

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.CannotEstablishConnectionException
import com.github.msemys.esjc.CatchUpSubscription
import com.github.msemys.esjc.CatchUpSubscriptionListener
import com.github.msemys.esjc.CatchUpSubscriptionSettings
import com.github.msemys.esjc.ConnectionClosedException
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreException
import com.github.msemys.esjc.ResolvedEvent
import com.github.msemys.esjc.SliceReadStatus
import com.github.msemys.esjc.StreamEventsSlice
import com.github.msemys.esjc.SubscriptionDropReason
import com.github.msemys.esjc.node.cluster.ClusterException
import com.github.msemys.esjc.operation.AccessDeniedException
import com.github.msemys.esjc.operation.CommandNotExpectedException
import com.github.msemys.esjc.operation.InvalidTransactionException
import com.github.msemys.esjc.operation.NoResultException
import com.github.msemys.esjc.operation.NotAuthenticatedException
import com.github.msemys.esjc.operation.ServerErrorException
import com.github.msemys.esjc.operation.StreamDeletedException
import com.github.msemys.esjc.operation.WrongExpectedVersionException
import com.github.msemys.esjc.operation.manager.OperationTimedOutException
import com.github.msemys.esjc.operation.manager.RetriesLimitReachedException
import com.github.msemys.esjc.subscription.MaximumSubscribersReachedException
import com.github.msemys.esjc.subscription.PersistentSubscriptionDeletedException
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.elder.sourcerer2.EventData
import org.elder.sourcerer2.EventId
import org.elder.sourcerer2.EventNormalizer
import org.elder.sourcerer2.EventRecord
import org.elder.sourcerer2.EventRepository
import org.elder.sourcerer2.EventSubscriptionUpdate
import org.elder.sourcerer2.ExpectedVersion
import org.elder.sourcerer2.RepositoryReadResult
import org.elder.sourcerer2.RepositoryVersion
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.StreamReadResult
import org.elder.sourcerer2.StreamVersion
import org.elder.sourcerer2.exceptions.PermanentEventReadException
import org.elder.sourcerer2.exceptions.PermanentEventWriteException
import org.elder.sourcerer2.exceptions.RetriableEventReadException
import org.elder.sourcerer2.exceptions.RetriableEventWriteException
import org.elder.sourcerer2.exceptions.UnexpectedVersionException
import org.elder.sourcerer2.utils.ElderPreconditions
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxEmitter
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Sourcerer event repository implementation using EventStore (geteventstore.com) as the underlying
 * system. The EventStore implementation uses Jackson to serialize and deserialize events that are
 * subclasses of the given event type base class. To instruct Jackson on how to correctly
 * deserialize events to the correct concrete sub type, please use either the Jaconson annotations,
 * or JAXB annotations as per
 * [Jackson Polymorphic
 * Deserialization](http://wiki.fasterxml.com/JacksonPolymorphicDeserialization)
 *
 * @param <T> The type of events managed by the event repository.
</T> */
class EventStoreEsjcEventRepository<T : Any>(
        private val streamPrefix: String,
        private val eventStore: EventStore,
        private val eventClass: Class<T>,
        private val objectMapper: ObjectMapper,
        private val normalizer: EventNormalizer<T>?
) : EventRepository<T> {
    // TODO: Customize these settings
    private val timeoutMillis = DEFAULT_TIMEOUT_MILLIS
    private val defaultSubscriptionSettings =
            CatchUpSubscriptionSettings
                    .newBuilder()
                    .resolveLinkTos(true)
                    .build()
    private val categoryStreamName = "\$ce-$streamPrefix"

    override fun getEventType(): Class<T> {
        return eventClass
    }

    override fun readAll(
            version: RepositoryVersion?,
            maxEvents: Int
    ): RepositoryReadResult<T>? {
        return readInternal(
                categoryStreamName,
                version?.toEsVersion(),
                maxEvents,
                true)
                ?.toRepositoryResult()
    }

    override fun read(
            streamId: StreamId,
            version: StreamVersion?,
            maxEvents: Int
    ): StreamReadResult<T>? {
        return readInternal(
                toEsStreamId(streamId),
                version?.toEsVersion(),
                maxEvents,
                false)
                ?.toStreamResult()
    }

    override fun readFirst(streamId: StreamId): EventRecord<T>? {
        return readSingleInternal(toEsStreamId(streamId), 0, false)
    }

    override fun readLast(streamId: StreamId): EventRecord<T>? {
        return readSingleInternal(toEsStreamId(streamId), -1, false)
    }

    private fun readSingleInternal(
            internalStreamId: String,
            eventNumber: Int,
            resolveLinksTo: Boolean
    ): EventRecord<T>? {
        logger.debug(
                "Reading event {} from {} (in {})",
                eventNumber,
                internalStreamId,
                streamPrefix)

        val eventsSlice = completeReadFuture(
                eventStore.readStreamEventsBackward(
                        internalStreamId,
                        eventNumber,
                        1,
                        resolveLinksTo, null))

        if (eventsSlice.events.isEmpty()) {
            logger.debug(
                    "Reading {} (in {}) returned no event",
                    internalStreamId,
                    streamPrefix)
            return null
        }

        val event = eventsSlice.events[0]
        logger.debug(
                "Read event from {} (version {})",
                internalStreamId,
                event.originalEventNumber())
        return fromEsEvent(event)
    }

    private fun readInternal(
            internalStreamId: String,
            version: Int?,
            maxEvents: Int,
            resolveLinksTo: Boolean
    ): StreamEventsSlice? {
        // For reads, sourcerer specified the version of the last event already read, which will be
        // excluded from the new events read. Eventstore requires the version of the first event to
        // read instead - adjust this up by and to make this right
        val effectiveVersion = version?.let { it + 1 } ?: 0
        val maxEventsPerRead = Integer.min(maxEvents, MAX_MAX_EVENTS_PER_READ)
        logger.debug(
                "Reading from {} (in {}) (version {}) - effective max {}",
                internalStreamId,
                streamPrefix,
                version,
                maxEventsPerRead)

        val eventsSlice = completeReadFuture(
                eventStore.readStreamEventsForward(
                        internalStreamId,
                        effectiveVersion,
                        maxEventsPerRead,
                        resolveLinksTo))

        if (eventsSlice.status != SliceReadStatus.Success) {
            // Not found or deleted, same thing to us!
            logger.debug(
                    "Reading {} (in {}) returned status {}",
                    internalStreamId, streamPrefix, eventsSlice.status)
            return null
        }

        logger.debug(
                "Read {} events from {} (version {})",
                eventsSlice.events.size,
                internalStreamId,
                version)

        return eventsSlice
    }

    override fun append(
            streamId: StreamId,
            events: List<EventData<T>>,
            version: ExpectedVersion
    ): StreamVersion {
        Preconditions.checkNotNull(events)
        ElderPreconditions.checkNotEmpty(events)

        val esEvents = events.map { this.toEsEventData(it) }

        logger.debug(
                "Writing {} events to stream {} (in {}) (expected version {})",
                esEvents.size, streamId, streamPrefix, version)

        val result = completeWriteFuture(
                eventStore.appendToStream(
                        toEsStreamId(streamId),
                        version.toEsVersion(),
                        esEvents),
                version)

        val nextExpectedVersion = result.nextExpectedVersion
        logger.debug("Write successful, next expected version is {}", nextExpectedVersion)
        return StreamVersion.ofInt(nextExpectedVersion)
    }

    override fun getCurrentVersion(): RepositoryVersion? {
        return getStreamVersionInternal(categoryStreamName)?.let { RepositoryVersion.ofInt(it) }
    }

    override fun getCurrentVersion(streamId: StreamId): StreamVersion? {
        return getStreamVersionInternal(toEsStreamId(streamId))?.let { StreamVersion.ofInt(it) }
    }

    private fun getStreamVersionInternal(streamName: String): Int? {
        val streamEventsSlice = completeReadFuture(
                eventStore.readStreamEventsBackward(
                        streamName,
                        -1,
                        1,
                        false))
        return if (streamEventsSlice.status == SliceReadStatus.Success)
            streamEventsSlice.lastEventNumber
        else null
    }

    override fun getStreamPublisher(
            streamId: StreamId,
            fromVersion: StreamVersion?
    ): Publisher<EventSubscriptionUpdate<T>> {
        logger.info("Creating publisher for {} (in {}) (starting with version {})",
                streamId, streamPrefix, fromVersion)

        return Flux.create { emitter ->
            val subscription = eventStore.subscribeToStreamFrom(
                    toEsStreamId(streamId),
                    fromVersion?.toEsVersion(),
                    defaultSubscriptionSettings,
                    EmitterListener(emitter, "$streamPrefix-$streamId"))
            emitter.setCancellation {
                logger.info("Closing ESJC subscription (asynchronously)")
                subscription.stop()
            }
        }
    }

    override fun getPublisher(fromVersion: RepositoryVersion?): Publisher<EventSubscriptionUpdate<T>> {
        logger.info("Creating publisher for all events in {} (starting with version {})",
                streamPrefix, fromVersion)
        return Flux.create { emitter ->
            val subscription = eventStore.subscribeToStreamFrom(
                    categoryStreamName,
                    fromVersion?.toEsVersion(),
                    defaultSubscriptionSettings,
                    EmitterListener(emitter, "$streamPrefix-all"))
            emitter.setCancellation {
                logger.info("Closing ESJC subscription (asynchronously)")
                subscription.stop()
            }
        }
    }

    private fun toEsStreamId(streamId: StreamId): String {
        return "$streamPrefix-${streamId.identifier}"
    }

    private fun toEsEventData(eventData: EventData<T>): com.github.msemys.esjc.EventData {
        return com.github.msemys.esjc.EventData.newBuilder()
                .eventId(eventData.eventId.id)
                .type(eventData.eventType)
                .jsonData(toEsEvent(eventData.event))
                .jsonMetadata(toEsMetadata(eventData.metadata))
                .build()
    }

    private fun toEsMetadata(metadata: Map<String, String>): String {
        return jsonObjectToString(metadata)
    }

    private fun toEsEvent(event: T): String {
        return jsonObjectToString(event)
    }

    private fun jsonObjectToString(obj: Any): String {
        try {
            return objectMapper.writer().writeValueAsString(obj)
        } catch (ex: IOException) {
            throw RetriableEventWriteException("Internal error writing event", ex)
        }
    }

    private fun StreamEventsSlice.toStreamResult(): StreamReadResult<T> {
        return StreamReadResult(
                ImmutableList.copyOf(events.map { fromEsEvent(it) }),
                // Next is the one after the last event we've ready in eventstore, we want the
                // reference to the one we've actually read as our reads are exclusive.
                StreamVersion.ofInt(nextEventNumber - 1),
                isEndOfStream
        )
    }

    private fun StreamEventsSlice.toRepositoryResult(): RepositoryReadResult<T> {
        return RepositoryReadResult(
                ImmutableList.copyOf(events.map { fromEsEvent(it) }),
                // Next is the one after the last event we've ready in eventstore, we want the
                // reference to the one we've actually read as our reads are exclusive.
                StreamVersion.ofInt(nextEventNumber - 1),
                isEndOfStream
        )
    }

    private fun fromEsStreamId(streamId: String): StreamId {
        // TODO: Ensure that we have a dash, handle mulitple ones sanely
        return StreamId.ofString(streamId.substring(streamId.indexOf('-') + 1))
    }

    private fun fromEsEvent(event: ResolvedEvent): EventRecord<T> {
        // We only know the version within the repository if this came from a subscription,
        // marked by the fact that this is a resolve event (from a projection) rather than a
        // plain one.
        val repositoryVersion =
                if (event.isResolved) event.link.eventNumber
                else null

        return EventRecord(
                EventId.ofUuid(event.event.eventId),
                fromEsStreamId(event.event.eventStreamId),
                StreamVersion.ofInt(event.event.eventNumber),
                repositoryVersion?.let { RepositoryVersion.ofInt(it) },
                event.event.eventType,
                event.event.created,
                fromEsMetadata(event.event.metadata),
                fromEsData(event.event.data))
    }

    private fun fromEsData(data: ByteArray): T {
        try {
            val rawEvent = objectMapper
                    .readerFor(eventClass)
                    .readValue<T>(data)
            return normalizeEvent(rawEvent)
        } catch (ex: IOException) {
            throw RetriableEventReadException("Internal error reading events", ex)
        }

    }

    private fun normalizeEvent(rawEvent: T): T {
        return if (normalizer != null) {
            normalizer.normalizeEvent(rawEvent)
        } else {
            rawEvent
        }
    }

    private fun fromEsMetadata(metadata: ByteArray?): ImmutableMap<String, String> {
        if (metadata == null || metadata.size == 0) {
            return ImmutableMap.of()
        }

        try {
            return ImmutableMap.copyOf(objectMapper
                    .readerFor(object : TypeReference<Map<String, String>>() {
                    })
                    .readValue<Map<String, String>>(metadata))
        } catch (ex: IOException) {
            throw RetriableEventReadException("Internal error reading events", ex)
        }

    }

    private fun <U> completeReadFuture(future: CompletableFuture<U>): U {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RetriableEventReadException("Internal error reading event", ex)
        } catch (ex: ExecutionException) {
            if (ex.cause is EventStoreException) {
                if (ex.cause is AccessDeniedException
                        || ex.cause is CommandNotExpectedException
                        || ex.cause is InvalidTransactionException
                        || ex.cause is NoResultException
                        || ex.cause is NotAuthenticatedException
                        || ex.cause is PersistentSubscriptionDeletedException
                        || ex.cause is StreamDeletedException) {
                    throw PermanentEventReadException(ex.cause)
                } else if (ex.cause is CannotEstablishConnectionException
                        || ex.cause is ClusterException
                        || ex.cause is ConnectionClosedException
                        || ex.cause is OperationTimedOutException
                        || ex.cause is MaximumSubscribersReachedException
                        || ex.cause is RetriesLimitReachedException
                        || ex.cause is ServerErrorException) {
                    throw RetriableEventReadException(ex.cause)
                } else {
                    logger.warn("Unrecognized event store exception reading events", ex.cause)
                    throw RetriableEventReadException(ex.cause)
                }
            } else if (ex.cause is RuntimeException) {
                logger.warn("Unrecognized runtime exception reading events", ex.cause)
                throw RetriableEventReadException(ex.cause)
            } else {
                logger.warn("Unrecognized exception reading events", ex.cause)
                throw RetriableEventReadException(
                        "Internal error reading events",
                        ex.cause)
            }
        } catch (ex: TimeoutException) {
            throw RetriableEventReadException("Timeout reading events", ex.cause)
        }

    }

    private fun <U> completeWriteFuture(
            future: CompletableFuture<U>,
            expectedVersion: ExpectedVersion
    ): U {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RetriableEventWriteException("Internal error writing event", ex)
        } catch (ex: ExecutionException) {
            if (ex.cause is EventStoreException) {
                if (ex.cause is WrongExpectedVersionException) {
                    logger.warn("Unexpected version when completing EventStore write", ex.cause)
                    throw UnexpectedVersionException(
                            ex.cause, null,
                            expectedVersion)
                } else if (ex.cause is AccessDeniedException
                        || ex.cause is CommandNotExpectedException
                        || ex.cause is InvalidTransactionException
                        || ex.cause is NoResultException
                        || ex.cause is NotAuthenticatedException
                        || ex.cause is PersistentSubscriptionDeletedException
                        || ex.cause is StreamDeletedException) {
                    throw PermanentEventWriteException(ex.cause)
                } else if (ex.cause is CannotEstablishConnectionException
                        || ex.cause is ClusterException
                        || ex.cause is ConnectionClosedException
                        || ex.cause is OperationTimedOutException
                        || ex.cause is MaximumSubscribersReachedException
                        || ex.cause is RetriesLimitReachedException
                        || ex.cause is ServerErrorException) {
                    throw RetriableEventWriteException(ex.cause)
                } else {
                    logger.warn("Unrecognized event store exception writing events", ex.cause)
                    throw RetriableEventWriteException(ex.cause)
                }
            } else if (ex.cause is RuntimeException) {
                logger.warn("Unrecognized runtime exception writing events", ex.cause)
                throw RetriableEventWriteException(ex.cause)
            } else {
                logger.warn("Unrecognized exception writing events", ex.cause)
                throw RetriableEventWriteException(
                        "Internal error writing events",
                        ex.cause)
            }
        } catch (ex: TimeoutException) {
            throw RetriableEventWriteException("Timeout writing events", ex.cause)
        }

    }

    private inner class EmitterListener(
            private val emitter: FluxEmitter<EventSubscriptionUpdate<T>>,
            private val name: String) : CatchUpSubscriptionListener {

        override fun onEvent(subscription: CatchUpSubscription, event: ResolvedEvent) {
            logger.debug("Incoming message in {}: {}", name, event)
            emitter.next(EventSubscriptionUpdate.ofEvent(fromEsEvent(event)))
        }

        override fun onClose(
                subscription: CatchUpSubscription?,
                reason: SubscriptionDropReason?,
                exception: Exception?) {
            if (exception != null) {
                logger.error(
                        "Subscription $name failed with reason $reason",
                        exception)
            } else {
                logger.error(
                        "Subscription {} failed with reason {} and no exception",
                        name,
                        reason)
            }

            emitter.fail(EventStoreSubscriptionStoppedException(reason, exception))
        }

        override fun onLiveProcessingStarted(subscription: CatchUpSubscription?) {
            logger.info("Live processing started for {}!", name)
            emitter.next(EventSubscriptionUpdate.caughtUp<T>())
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventStoreEsjcEventRepository::class.java)

        private const val MAX_MAX_EVENTS_PER_READ = 4095
        private const val DEFAULT_TIMEOUT_MILLIS = 30 * 1000.toLong()
    }
}

private fun ExpectedVersion?.toEsVersion(): com.github.msemys.esjc.ExpectedVersion {
    return when (this) {
        null -> com.github.msemys.esjc.ExpectedVersion.ANY
        ExpectedVersion.Any -> com.github.msemys.esjc.ExpectedVersion.ANY
        ExpectedVersion.AnyExisting -> com.github.msemys.esjc.ExpectedVersion.STREAM_EXISTS
        ExpectedVersion.NotCreated -> com.github.msemys.esjc.ExpectedVersion.NO_STREAM
        is ExpectedVersion.Exactly ->
            com.github.msemys.esjc.ExpectedVersion.of(streamVersion.toEsVersion())
    }
}

private fun RepositoryVersion.toEsVersion(): Int {
    return this.version.toEsVersion()
}

private fun StreamVersion.toEsVersion(): Int {
    return this.version.toEsVersion()
}

private fun String.toEsVersion(): Int {
    try {
        return Integer.parseInt(this)
    } catch (ex: NumberFormatException) {
        throw IllegalArgumentException(
                "Version provided (${this}) is not compatible with the ESJC driver",
                ex)
    }
}
