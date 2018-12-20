package org.elder.sourcerer2.esjc

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.elder.sourcerer2.AggregateProjection
import org.elder.sourcerer2.AggregateRepository
import org.elder.sourcerer2.EventType
import org.elder.sourcerer2.ExpectedVersion
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.exceptions.UnexpectedVersionException
import org.elder.sourcerer2.extras.EventStreams
import org.elder.sourcerer2.esjc.utils.ConcurrencyProgress
import org.elder.sourcerer2.esjc.utils.ConcurrencyRule
import org.elder.sourcerer2.esjc.utils.TestEventStore
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Assert.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.reflect.KClass

class EventStreamsIntegrationTest {
    private val eventStore = TestEventStore()
    private val randomId = StreamId.ofString(UUID.randomUUID().toString())
    private val then = this

    private lateinit var aggregateRepository: AggregateRepository<State, Event>
    private lateinit var streams: EventStreams<State, Event>

    @Rule
    @JvmField
    val concurrency = ConcurrencyRule()

    @Before
    fun setup() {
        aggregateRepository = eventStore
                .createAggregateRepository("test_eventstreams",
                        Projection())
        streams = EventStreams(aggregateRepository)
    }

    @Test
    fun `can create new stream`() {
        createWith { Event.ValueSet("the-value") }

        then assertState { value equals "the-value" }
    }

    @Test
    fun `cannot create another stream using existing name`() {
        createWith { Event.ValueSet("the-value") }

        expectError(UnexpectedVersionException::class) {
            createWith { Event.ValueSet("the-value-2") }
        }
    }

    @Test
    fun `can update existing stream`() {
        createWith { Event.ValueSet("the-value") }

        updateWith { Event.ValueSet("the-value-2") }

        then assertState { value equals "the-value-2" }
    }

    @Test
    fun `cannot update non-existing stream by default`() {
        expectError(UnexpectedVersionException::class) {
            updateWith { Event.ValueSet("the-value") }
        }
    }

    @Test
    fun `can update non-existing stream if explicitly allowed`() {
        updateWith(ExpectedVersion.any()) {
            Event.ValueSet("the-value")
        }

        then assertState { value equals "the-value" }
    }

    @Test
    fun `can update stream conditionally on current version`() {
        createWith { Event.ValueSet("the-value") }
        val version = updateWith(ExpectedVersion.any()) {
            Event.ValueSet("the-value-2")
        }
                .newVersion

        updateWith(ExpectedVersion.exactly(version!!)) {
            Event.ValueSet("the-value-3")
        }

        then assertState { value equals "the-value-3" }
    }

    @Test
    fun `update fails when version expectation not met`() {
        val version = createWith { Event.ValueSet("the-value") }.newVersion
        updateWith(ExpectedVersion.any()) {
            Event.ValueSet("the-value-2")
        }

        expectError(UnexpectedVersionException::class) {
            updateWith(ExpectedVersion.exactly(version!!)) {
                Event.ValueSet("the-value-3")
            }
        }
    }

    @Test
    fun `can append event to streams`() {
        appendWith { Event.ValueSet("the-value") }
        then assertState { value equals "the-value" }

        appendWith { Event.ValueSet("the-value-2") }
        then assertState { value equals "the-value-2" }
    }

    @Test
    fun `concurrent updates fail`() {
        createWith { Event.ValueSet("the-value") }

        val slowUpdateStarted = ConcurrencyProgress("slow read")
        val sneakyUpdateCompleted = ConcurrencyProgress("sneaky update")

        concurrency.runInThread("slow update") {
            expectError(UnexpectedVersionException::class) {
                updateWith {
                    slowUpdateStarted.happened()
                    sneakyUpdateCompleted.await()
                    Event.ValueSet("the-value-slow")
                }
            }
        }

        concurrency.runInThread("sneaky update") {
            slowUpdateStarted.await()
            updateWith {
                Event.ValueSet("the-value-sneaky")
            }
            sneakyUpdateCompleted.happened()
        }
    }

    @Test
    fun `concurrent creates fail`() {
        val slowCreateStarted = ConcurrencyProgress("slow read")
        val sneakyCreateCompleted = ConcurrencyProgress("sneaky update")

        concurrency.runInThread("slow create") {
            expectError(UnexpectedVersionException::class) {
                createWith {
                    slowCreateStarted.happened()
                    sneakyCreateCompleted.await()
                    Event.ValueSet("the-value-slow")
                }
            }
        }

        concurrency.runInThread("sneaky create") {
            slowCreateStarted.await()
            createWith {
                Event.ValueSet("the-value-sneaky")
            }
            sneakyCreateCompleted.happened()
        }
    }

    private fun createWith(event: () -> Event) =
            streams.create(randomId) { state -> state.apply(event) }

    private fun updateWith(event: () -> Event) =
            streams.update(randomId) { state -> state.apply(event) }

    private fun updateWith(expectedVersion: ExpectedVersion, event: () -> Event) =
            streams.update(randomId, expectedVersion) { state -> state.apply(event) }

    private fun appendWith(event: () -> Event) = streams.append(randomId) { listOf(event()) }

    private fun expectError(expected: KClass<out RuntimeException>, action: () -> Any) {
        try {
            action()
            fail("Expected exception to be thrown")
        } catch (exception: Exception) {
            assertThat(exception, instanceOf(expected.java))
        }
    }

    class Projection : AggregateProjection<State, Event> {
        override fun apply(id: StreamId, state: State, event: Event): State {
            return when (event) {
                is Event.ValueSet -> state.copy(value = event.value)
            }
        }

        override fun empty() = State()
    }

    data class State(val value: String? = null)

    private infix fun Any?.equals(other: Any?) {
        assertThat(this, equalTo(other))
    }

    @EventType(repositoryName = "testRepository")
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type",
            visible = true)
    @JsonSubTypes(JsonSubTypes.Type(Event.ValueSet::class))
    sealed class Event {
        data class ValueSet(val value: String) : Event()
    }

    private infix fun assertState(checks: State.() -> Unit) {
        val agg = aggregateRepository.load(randomId)
        val state = agg.state()
        state.checks()
    }
}
