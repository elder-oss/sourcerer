package org.elder.sourcerer.eventstore.tests

import org.elder.sourcerer.*
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

abstract class EventSubscriptionIntegrationTestBase {
    protected abstract fun createRepositoryFactory(sessionId: String): EventRepositoryFactory

    @Test
    fun subscriptionRecoversWhenErrorIsThrown() {
        val sessionId = randomSessionId()
        val sentEvents = (0 until 1000)
                .map { TestEventType(value = UUID.randomUUID().toString()) }
                .toList()

        createRepositoryFactory(sessionId).use { producerRepoFactory ->
            val producerRepo = producerRepoFactory.getEventRepository(TestEventType::class.java)
            val streamId = UUID.randomUUID().toString()
            producerRepo.append(streamId, sentEvents.map { eventData(it) }, ExpectedVersion.any())
        }

        createRepositoryFactory(sessionId).use { subscriberRepoFactory ->
            val subscriberRepo = subscriberRepoFactory.getEventRepository(TestEventType::class.java)
            val receivedEvents = mutableListOf<TestEventType>()
            var position = AtomicInteger(-1)
            var batch = AtomicInteger(0)

            val handler = object : EventSubscriptionHandlerBase<TestEventType>() {
                override fun processEvents(eventRecords: List<EventRecord<TestEventType>>) {
                    if (batch.incrementAndGet() % 2 == 0) {
                        // Fail every other time...
                        throw RuntimeException("Things are very very bad")
                    }

                    synchronized(receivedEvents) {
                        eventRecords.forEach {
                            receivedEvents.add(it.event)
                            position.set(it.streamVersion)
                        }
                    }
                }
            }

            val subscription = DefaultEventSubscription(subscriberRepo, handler,
                    SubscriptionWorkerConfig(100, 1, 10))
            subscription.setPositionSource {
                val pos = position.get()
                if (pos < 0) null else pos
            }
            subscription.start()

            Thread.sleep(10000)
            val actuallyReceived = synchronized(receivedEvents) { receivedEvents.toList() }
            Assert.assertThat(actuallyReceived.size, CoreMatchers.equalTo(sentEvents.size))
            Assert.assertThat(actuallyReceived, CoreMatchers.equalTo(sentEvents))
        }
    }
}