package org.elder.sourcerer.escj.kotlin

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.elder.sourcerer.EventData
import org.elder.sourcerer.EventNormalizer
import org.elder.sourcerer.EventReadResult
import org.elder.sourcerer.EventRecord
import org.elder.sourcerer.EventRepository
import org.elder.sourcerer.EventSubscriptionUpdate
import org.elder.sourcerer.ExpectedVersion
import org.elder.sourcerer.ExpectedVersionType
import org.elder.sourcerer.exceptions.PermanentEventReadException
import org.elder.sourcerer.exceptions.PermanentEventWriteException
import org.elder.sourcerer.exceptions.RetriableEventReadException
import org.elder.sourcerer.exceptions.RetriableEventWriteException
import org.elder.sourcerer.exceptions.UnexpectedVersionException
import org.elder.sourcerer.utils.ElderPreconditions
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxEmitter

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
 */
class EventStoreEsjcEventRepository<T : Any>(
        private val streamPrefix: String,
        private val eventStore: EventStore,
        private val eventClass: Class<T>,
        private val objectMapper: ObjectMapper,
        private val normalizer: EventNormalizer<T>?
) : EventRepository<T> {
    private val timeoutMillis: Long
    private val defaultSubscriptionSettings: CatchUpSubscriptionSettings

    private val categoryStreamName: String
        get() = "\$ce-$streamPrefix"

    init {
        this.timeoutMillis = DEFAULT_TIMEOUT_MILLIS
        // TODO: Customize these settings
        defaultSubscriptionSettings = CatchUpSubscriptionSettings.newBuilder()
                .resolveLinkTos(true)
                .build()
    }

    override fun getEventType(): Class<T> {
        return eventClass
    }

    override fun readAll(version: Int, maxEvents: Int): EventReadResult<T>? {
        return readInternal(categoryStreamName, version, maxEvents, true)
    }

    override fun read(streamId: String, version: Int, maxEvents: Int): EventReadResult<T>? {
        return readInternal(toEsStreamId(streamId), version, maxEvents, false)
    }

    override fun readFirst(streamId: String): EventRecord<T>? {
        return readSingleInternal(toEsStreamId(streamId), 0, false)
    }

    override fun readLast(streamId: String): EventRecord<T>? {
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
                streamPrefix
        )

        val eventsSlice = completeReadFuture(
                eventStore.readStreamEventsBackward(
                        internalStreamId,
                        eventNumber,
                        1,
                        resolveLinksTo,
                        null
                ),
                ExpectedVersion.any()
        )

        if (eventsSlice.events.isEmpty()) {
            logger.debug(
                    "Reading {} (in {}) returned no event",
                    internalStreamId,
                    streamPrefix
            )
            return null
        }

        val event = eventsSlice.events[0]
        logger.debug(
                "Read event from {} (version {})",
                internalStreamId,
                event.originalEventNumber()
        )
        return fromEsEvent(event)
    }

    private fun readInternal(
            internalStreamId: String,
            version: Int,
            maxEvents: Int,
            resolveLinksTo: Boolean
    ): EventReadResult<T>? {
        val maxEventsPerRead = Integer.min(maxEvents, MAX_MAX_EVENTS_PER_READ)
        logger.debug(
                "Reading from {} (in {}) (version {}) - effective max {}",
                internalStreamId,
                streamPrefix,
                version,
                maxEventsPerRead
        )

        val eventsSlice = completeReadFuture(
                eventStore.readStreamEventsForward(
                        internalStreamId,
                        version,
                        maxEventsPerRead,
                        resolveLinksTo
                ),
                ExpectedVersion.exactly(version)
        )

        if (eventsSlice.status != SliceReadStatus.Success) {
            // Not found or deleted, same thing to us!
            logger.debug(
                    "Reading {} (in {}) returned status {}",
                    internalStreamId, streamPrefix, eventsSlice.status
            )
            return null
        }

        logger.debug(
                "Read {} events from {} (version {})",
                eventsSlice.events.size,
                internalStreamId,
                version
        )
        val events = ImmutableList.copyOf(eventsSlice.events.map { fromEsEvent(it) })
        return EventReadResult(
                events,
                eventsSlice.fromEventNumber,
                eventsSlice.lastEventNumber,
                eventsSlice.nextEventNumber,
                eventsSlice.isEndOfStream
        )
    }

    override fun append(
            streamId: String,
            events: List<EventData<T>>,
            version: ExpectedVersion
    ): Int {
        Preconditions.checkNotNull(events)
        ElderPreconditions.checkNotEmpty(events)

        val esEvents = events.map { toEsEventData(it) }

        logger.debug(
                "Writing {} events to stream {} (in {}) (expected version {})",
                esEvents.size, streamId, streamPrefix, version
        )
        try {
            val result = completeWriteFuture(
                    eventStore.appendToStream(
                            toEsStreamId(streamId),
                            toEsVersion(version),
                            esEvents
                    ),
                    version
            )

            val nextExpectedVersion = result.nextExpectedVersion
            logger.debug("Write successful, next expected version is {}", nextExpectedVersion)
            return nextExpectedVersion
        } catch (ex: WrongExpectedVersionException) {
            logger.warn("Unexpected version when attempting append", ex)
            throw UnexpectedVersionException(ex.message, version)
        }

    }

    override fun getCurrentVersion(): Int {
        return getStreamVersionInternal(categoryStreamName)
    }

    override fun getCurrentVersion(streamId: String): Int {
        return getStreamVersionInternal(toEsStreamId(streamId))
    }

    private fun getStreamVersionInternal(streamName: String): Int {
        val streamEventsSlice = completeReadFuture(
                eventStore.readStreamEventsBackward(
                        streamName,
                        -1,
                        1,
                        false
                ),
                ExpectedVersion.any()
        )
        return streamEventsSlice.lastEventNumber
    }

    override fun getStreamPublisher(
            streamId: String,
            fromVersion: Int?
    ): Publisher<EventSubscriptionUpdate<T>> {
        logger.info(
                "Creating publisher for {} (in {}) (starting with version {})",
                streamId, streamPrefix, fromVersion
        )

        return Flux.create { emitter ->
            val subscription = eventStore.subscribeToStreamFrom(
                    toEsStreamId(streamId),
                    fromVersion,
                    defaultSubscriptionSettings,
                    EmitterListener(emitter, "$streamPrefix-$streamId")
            )
            emitter.setCancellation {
                logger.info("Closing ESJC subscription (asynchronously)")
                subscription.stop()
            }
        }
    }

    override fun publishTo(
            streamId: String,
            fromVersion: Int?
    ): Publisher<EventSubscriptionUpdate<T>> {
        val channel = Channel<EventSubscriptionUpdate<T>>(1000)
        val coroutineScope = CoroutineScope(EmptyCoroutineContext)

        return Publisher { subscriber ->
            val subscription = eventStore.subscribeToStreamFrom(
                    toEsStreamId(streamId),
                    fromVersion,
                    defaultSubscriptionSettings,
                    ChannelListener(channel, "$streamPrefix-$streamId", coroutineScope)
            )
            coroutineScope.launch {
                subscriber.onNext(channel.receive())
            }
            channel.invokeOnClose {
                logger.info("Closing ESJC subscription (asynchronously)")
                subscription.stop()
                if (it == null) {
                    subscriber.onComplete()
                } else {
                    subscriber.onError(it)
                }
            }
        }
    }

    override fun getPublisher(fromVersion: Int?): Publisher<EventSubscriptionUpdate<T>> {
        logger.info(
                "Creating publisher for all events in {} (starting with version {})",
                streamPrefix, fromVersion
        )
        return Flux.create { emitter ->
            val subscription = eventStore.subscribeToStreamFrom(
                    categoryStreamName,
                    fromVersion,
                    defaultSubscriptionSettings,
                    EmitterListener(emitter, "$streamPrefix-all")
            )
            emitter.setCancellation {
                logger.info("Closing ESJC subscription (asynchronously)")
                subscription.stop()
            }
        }
    }

    private fun toEsStreamId(streamId: String): String {
        return "$streamPrefix-$streamId"
    }

    private fun toEsEventData(eventData: EventData<T>): com.github.msemys.esjc.EventData {
        return com.github.msemys.esjc.EventData.newBuilder()
                .eventId(eventData.eventId)
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

    private fun toEsVersion(version: ExpectedVersion?): com.github.msemys.esjc.ExpectedVersion {
        return if (version == null) {
            com.github.msemys.esjc.ExpectedVersion.ANY
        } else {
            when (version.type) {
                ExpectedVersionType.ANY -> com.github.msemys.esjc.ExpectedVersion.ANY
                ExpectedVersionType.EXACTLY -> com.github.msemys.esjc.ExpectedVersion.of(version.expectedVersion)
                ExpectedVersionType.NOT_CREATED -> com.github.msemys.esjc.ExpectedVersion.NO_STREAM
                else -> throw IllegalArgumentException(
                        "Unrecognized expected version type: $version"
                )
            }
        }
    }

    private fun fromEsStreamId(streamId: String): String {
        // TODO: Ensure that we have a dash, handle mulitple ones sanely
        return streamId.substring(streamId.indexOf('-') + 1)
    }

    private fun fromEsEvent(event: ResolvedEvent): EventRecord<T> {
        val streamVersion: Int
        val aggregateVersion: Int
        if (event.isResolved) {
            aggregateVersion = event.event.eventNumber
            streamVersion = event.link.eventNumber
        } else {
            aggregateVersion = event.event.eventNumber
            streamVersion = event.event.eventNumber
        }

        return EventRecord(
                fromEsStreamId(event.event.eventStreamId),
                streamVersion,
                aggregateVersion,
                event.event.eventType,
                event.event.eventId,
                event.event.created,
                fromEsMetadata(event.event.metadata),
                fromEsData(event.event.data)
        )
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
        if (metadata == null || metadata.isEmpty()) {
            return ImmutableMap.of()
        }

        try {
            return ImmutableMap.copyOf<String, String>(
                    objectMapper
                            .readerFor(object : TypeReference<Map<String, String>>() {})
                            .readValue<Map<String, String>>(metadata)
            )
        } catch (ex: IOException) {
            throw RetriableEventReadException("Internal error reading events", ex)
        }
    }

    private fun <U> completeReadFuture(
            future: CompletableFuture<U>,
            expectedVersion: ExpectedVersion
    ): U {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RetriableEventReadException("Internal error reading event", ex)
        } catch (ex: ExecutionException) {
            when (ex.cause) {
                is EventStoreException -> {
                    when (ex.cause) {
                        is WrongExpectedVersionException -> {
                            throw UnexpectedVersionException(ex.cause, expectedVersion)
                        }

                        // Permanent failures
                        is AccessDeniedException,
                        is CommandNotExpectedException,
                        is InvalidTransactionException,
                        is NoResultException,
                        is NotAuthenticatedException,
                        is PersistentSubscriptionDeletedException,
                        is StreamDeletedException -> {
                            throw PermanentEventReadException(ex.cause)
                        }

                        // Retryable failures
                        is CannotEstablishConnectionException,
                        is ClusterException,
                        is ConnectionClosedException,
                        is OperationTimedOutException,
                        is MaximumSubscribersReachedException,
                        is RetriesLimitReachedException,
                        is ServerErrorException -> {
                            throw RetriableEventReadException(ex.cause)
                        }

                        else -> {
                            logger.warn(
                                    "Unrecognized event store exception reading events",
                                    ex.cause
                            )
                            throw RetriableEventReadException(ex.cause)
                        }
                    }
                }
                is RuntimeException -> {
                    logger.warn("Unrecognized runtime exception reading events", ex.cause)
                    throw RetriableEventReadException(ex.cause)
                }
                else -> {
                    logger.warn("Unrecognized exception reading events", ex.cause)
                    throw RetriableEventReadException(
                            "Internal error reading events",
                            ex.cause
                    )
                }
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
                    throw UnexpectedVersionException(ex.cause, expectedVersion)
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
                        ex.cause
                )
            }
        } catch (ex: TimeoutException) {
            throw RetriableEventWriteException("Timeout writing events", ex.cause)
        }

    }

    private inner class ChannelListener(
            private val channel: Channel<EventSubscriptionUpdate<T>>,
            private val name: String,
            private val coroutineScope: CoroutineScope
    ) : CatchUpSubscriptionListener, CoroutineScope by coroutineScope {

        override fun onEvent(subscription: CatchUpSubscription, event: ResolvedEvent) {
            logger.debug("Incoming message in {}: {}", name, event)
            launch {
                channel.send(EventSubscriptionUpdate.ofEvent(fromEsEvent(event)))
            }
        }

        override fun onClose(
                subscription: CatchUpSubscription?,
                reason: SubscriptionDropReason?,
                exception: Exception?
        ) {
            if (exception != null) {
                logger.error("Subscription $name failed with reason $reason", exception)
            } else {
                logger.error("Subscription {} failed with reason {} and no exception", name, reason)
            }

            channel.close(EventStoreSubscriptionStoppedException(reason, exception))
        }

        override fun onLiveProcessingStarted(subscription: CatchUpSubscription?) {
            logger.info("Live processing started for {}!", name)
            launch {
                channel.send(EventSubscriptionUpdate.caughtUp())
            }
        }
    }

    private inner class EmitterListener(
            private val emitter: FluxEmitter<EventSubscriptionUpdate<T>>,
            private val name: String
    ) : CatchUpSubscriptionListener {

        override fun onEvent(subscription: CatchUpSubscription, event: ResolvedEvent) {
            logger.debug("Incoming message in {}: {}", name, event)
            emitter.next(EventSubscriptionUpdate.ofEvent(fromEsEvent(event)))
        }

        override fun onClose(
                subscription: CatchUpSubscription?,
                reason: SubscriptionDropReason?,
                exception: Exception?
        ) {
            if (exception != null) {
                logger.error(
                        "Subscription $name failed with reason $reason",
                        exception
                )
            } else {
                logger.error(
                        "Subscription {} failed with reason {} and no exception",
                        name,
                        reason
                )
            }

            emitter.fail(EventStoreSubscriptionStoppedException(reason, exception))
        }

        override fun onLiveProcessingStarted(subscription: CatchUpSubscription?) {
            logger.info("Live processing started for {}!", name)
            emitter.next(EventSubscriptionUpdate.caughtUp())
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventStoreEsjcEventRepository::class.java)

        private const val MAX_MAX_EVENTS_PER_READ = 4095
        private const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}
