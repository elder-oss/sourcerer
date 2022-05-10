package org.elder.sourcerer.eventstore.tests

import org.elder.sourcerer.EventData
import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.ExpectedVersion
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Assert
import org.junit.Test
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

abstract class EventStorEventRepositoryIntegrationTestBase(
        val enableLegacyTcpInterface: Boolean = false
) {
    protected abstract fun createRepositoryFactory(port: Int): EventRepositoryFactory

    private fun eventstoreInstance() = EventstoreInstance(enableLegacyTcpInterface = enableLegacyTcpInterface)

    @Test
    fun canReadWriteSingleEvent() {
        eventstoreInstance().use { eventstoreDb ->
            eventstoreDb.ensureStarted()
            val repoFactory = createRepositoryFactory(eventstoreDb.port)
            val testRepo = repoFactory.getEventRepository(TestEventType::class.java)
            val streamId = UUID.randomUUID().toString()

            val event = TestEventType("one")
            testRepo.append(streamId, listOf(eventData(event)), ExpectedVersion.any())
            val events = testRepo.read(streamId)

            Assert.assertThat(events.events.size, equalTo(1))
            Assert.assertThat(events.events[0].event, equalTo(event))
        }
    }

    @Test
    fun canReadBatched() {
        eventstoreInstance().use { eventstoreDb ->
            eventstoreDb.ensureStarted()
            val repoFactory = createRepositoryFactory(eventstoreDb.port)
            val testRepo = repoFactory.getEventRepository(TestEventType::class.java)
            val streamId = UUID.randomUUID().toString()
            val writtenEvents = (0 until 1000)
                    .map { TestEventType(value = UUID.randomUUID().toString()) }
                    .toList()

            testRepo.append(streamId, writtenEvents.map { eventData(it) }, ExpectedVersion.any())

            val readEvents = mutableListOf<TestEventType>()
            var currentVersion = 0
            while (true) {
                val batchResults = testRepo.read(streamId, currentVersion, 64)
                readEvents.addAll(batchResults.events.map { it.event })
                currentVersion = batchResults.nextVersion
                if (batchResults.isEndOfStream) break
            }

            Assert.assertThat(readEvents, equalTo(writtenEvents))
        }
    }

    @Test
    fun canReadBatchedWhenPageSizeMatchesEventsExactly() {
        eventstoreInstance().use { eventstoreDb ->
            eventstoreDb.ensureStarted()
            val repoFactory = createRepositoryFactory(eventstoreDb.port)
            val testRepo = repoFactory.getEventRepository(TestEventType::class.java)
            val streamId = UUID.randomUUID().toString()
            val writtenEvents = (0 until 100)
                    .map { TestEventType(value = UUID.randomUUID().toString()) }
                    .toList()

            testRepo.append(streamId, writtenEvents.map { eventData(it) }, ExpectedVersion.any())

            val readEvents = mutableListOf<TestEventType>()
            var currentVersion = 0
            while (true) {
                val batchResults = testRepo.read(streamId, currentVersion, 100)
                readEvents.addAll(batchResults.events.map { it.event })
                currentVersion = batchResults.nextVersion
                if (batchResults.isEndOfStream) break
            }

            Assert.assertThat(readEvents, equalTo(writtenEvents))
        }
    }

    @Test
    fun canConsumeEventsFromCategorySubscriptionRealTime() {
        eventstoreInstance().use { eventstoreDb ->
            eventstoreDb.ensureStarted()
            val produceRepoFactory = createRepositoryFactory(eventstoreDb.port)
            val produceRepo = produceRepoFactory.getEventRepository(TestEventType::class.java)
            val consumeRepoFactory = createRepositoryFactory(eventstoreDb.port)
            val consumeRepo = consumeRepoFactory.getEventRepository(TestEventType::class.java)

            // Set up subscriber to collect events
            val receivedEvents = mutableListOf<TestEventType>()
            val subscription = Flux.from(consumeRepo.getPublisher(null))
                    .publishOn(Schedulers.parallel())
                    .subscribe {
                        synchronized(receivedEvents) {
                            receivedEvents.add(it.event.event)
                        }
                    }

            // Publish us some data
            val sentEvents = mutableListOf<TestEventType>()
            (0 until 1000).forEach {
                val event = TestEventType(value = UUID.randomUUID().toString())
                val stream = UUID.randomUUID().toString()
                produceRepo.append(stream, listOf(eventData(event)), ExpectedVersion.any())
                sentEvents.add(event)
            }

            // Give the world a moment to catch up
            Thread.sleep(5000)
            subscription.dispose()

            // We should now have seen all of the events
            val actuallyReceived = synchronized(receivedEvents) { receivedEvents.toList() }
            Assert.assertThat(actuallyReceived, equalTo(sentEvents))
        }
    }

    @Test
    fun canConsumeEventsFromCategorySubscriptionCatchUp() {
        eventstoreInstance().use { eventstoreDb ->
            eventstoreDb.ensureStarted()
            val produceRepoFactory = createRepositoryFactory(eventstoreDb.port)
            val produceRepo = produceRepoFactory.getEventRepository(TestEventType::class.java)
            val consumeRepoFactory = createRepositoryFactory(eventstoreDb.port)
            val consumeRepo = consumeRepoFactory.getEventRepository(TestEventType::class.java)

            // Publish us some data
            val sentEvents = mutableListOf<TestEventType>()
            (0 until 1000).forEach {
                val event = TestEventType(value = UUID.randomUUID().toString())
                val stream = UUID.randomUUID().toString()
                produceRepo.append(stream, listOf(eventData(event)), ExpectedVersion.any())
                sentEvents.add(event)
            }

            // Set up subscriber to collect events
            val receivedEvents = mutableListOf<TestEventType>()
            val subscription = Flux.from(consumeRepo.getPublisher(null))
                    .publishOn(Schedulers.parallel())
                    .subscribe {
                        synchronized(receivedEvents) {
                            receivedEvents.add(it.event.event)
                        }
                    }

            // Give the world a moment to catch up
            Thread.sleep(5000)
            subscription.dispose()

            // We should now have seen all of the events
            val actuallyReceived = synchronized(receivedEvents) { receivedEvents.toList() }
            Assert.assertThat(actuallyReceived, equalTo(sentEvents))
        }
    }

    @Test
    fun subscriptionFlagsErrorIfEventstoreDies() {
        eventstoreInstance().use { eventstoreDb ->
            eventstoreDb.ensureStarted()
            val produceRepoFactory = createRepositoryFactory(eventstoreDb.port)
            val produceRepo = produceRepoFactory.getEventRepository(TestEventType::class.java)
            val consumeRepoFactory = createRepositoryFactory(eventstoreDb.port)
            val consumeRepo = consumeRepoFactory.getEventRepository(TestEventType::class.java)

            // Publish us some data
            val sentEvents = mutableListOf<TestEventType>()
            (0 until 1000).forEach {
                val event = TestEventType(value = UUID.randomUUID().toString())
                val stream = UUID.randomUUID().toString()
                produceRepo.append(stream, listOf(eventData(event)), ExpectedVersion.any())
                sentEvents.add(event)
            }

            // Set up subscriber to collect events
            val receivedEventsCount = AtomicInteger(0)
            val receivedError = AtomicReference<Throwable>()
            val subscription = Flux.from(consumeRepo.getPublisher(null))
                    .publishOn(Schedulers.parallel())
                    .subscribe({
                        receivedEventsCount.incrementAndGet()
                    }, { error -> receivedError.set(error) })

            // Wait until we've seen at least one event ...
            while (true) {
                if (receivedEventsCount.get() > 0) break
                Thread.sleep(50)
            }

            // Kill the eventstore server dead!
            eventstoreDb.close()

            // Give the world a moment to catch up
            Thread.sleep(5000)

            // We should now have seen a meaningful error
            Assert.assertThat(receivedError.get(), notNullValue())
            subscription.dispose()
        }
    }

    private fun <T> eventData(event: T): EventData<T> {
        return EventData(
                "eventType",
                UUID.randomUUID(),
                mapOf(),
                event
        )
    }
}