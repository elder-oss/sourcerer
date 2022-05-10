package org.elder.sourcerer.eventstore.tests

import org.elder.sourcerer.EventData
import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.EventSubscriptionUpdate.UpdateType
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

/**
 * Base class for integration tests of the event repository interface across multiple implementations.
 */
abstract class EventRepositoryIntegrationTestBase {
    protected abstract fun createRepositoryFactory(sessionId: String): EventRepositoryFactory

    @Test
    fun canReadWriteSingleEvent() {
        createRepositoryFactory(randomSessionId()).use { repoFactory ->
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
        createRepositoryFactory(randomSessionId()).use { repoFactory ->
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
        createRepositoryFactory(randomSessionId()).use { repoFactory ->
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
        val sessionId = randomSessionId()
        createRepositoryFactory(sessionId).use { produceRepoFactory ->
            createRepositoryFactory(sessionId).use { consumeRepoFactory ->
                val produceRepo = produceRepoFactory.getEventRepository(TestEventType::class.java)
                val consumeRepo = consumeRepoFactory.getEventRepository(TestEventType::class.java)

                // Set up subscriber to collect events
                val receivedEvents = mutableListOf<TestEventType>()
                val subscription = Flux.from(consumeRepo.getPublisher(null))
                        .publishOn(Schedulers.parallel())
                        .subscribe {
                            if (it.updateType == UpdateType.EVENT) {
                                synchronized(receivedEvents) {
                                    receivedEvents.add(it.event.event)
                                }
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
                Assert.assertThat(actuallyReceived.size, equalTo(sentEvents.size))
                Assert.assertThat(actuallyReceived, equalTo(sentEvents))
            }
        }
    }

    @Test
    fun canConsumeEventsFromCategorySubscriptionCatchUp() {
        val sessionId = randomSessionId()
        createRepositoryFactory(sessionId).use { produceRepoFactory ->
            createRepositoryFactory(sessionId).use { consumeRepoFactory ->
                val produceRepo = produceRepoFactory.getEventRepository(TestEventType::class.java)
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
                            if (it.updateType == UpdateType.EVENT) {
                                synchronized(receivedEvents) {
                                    receivedEvents.add(it.event.event)
                                }
                            }
                        }

                // Give the world a moment to catch up
                Thread.sleep(5000)
                subscription.dispose()

                // We should now have seen all of the events
                val actuallyReceived = synchronized(receivedEvents) { receivedEvents.toList() }
                Assert.assertThat(actuallyReceived.size, equalTo(sentEvents.size))
                Assert.assertThat(actuallyReceived, equalTo(sentEvents))
            }
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