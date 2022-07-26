package org.elder.sourcerer.eventstore.tests

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.elder.sourcerer.*
import org.elder.sourcerer.eventstore.tests.utils.ConcurrencyProgress
import org.elder.sourcerer.eventstore.tests.utils.ConcurrencyRule
import org.elder.sourcerer.exceptions.UnexpectedVersionException
import org.elder.sourcerer.kotlin.CreateConflictStrategy
import org.elder.sourcerer.kotlin.EventStreams
import org.elder.sourcerer.utils.RetryPolicy
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * Base class for tests of higher level abstractions such as aggregate repositories, subclassed
 * by specific implementations.
 */
abstract class EventStreamsIntegrationTestBase() {
    private val randomId = UUID.randomUUID().toString()
    private val then = this

    private var repositoryFactory: EventRepositoryFactory? = null
    private lateinit var aggregateRepository: AggregateRepository<State, Event>
    private lateinit var streams: EventStreams<State, Event>

    protected abstract fun createRepositoryFactory(sessionId: String): EventRepositoryFactory

    @Rule
    @JvmField
    val concurrency = ConcurrencyRule()


    @Before
    fun setup() {
        repositoryFactory = createRepositoryFactory(randomSessionId())
        val repository = repositoryFactory!!.getEventRepository(Event::class.java)
        aggregateRepository = DefaultAggregateRepository(repository, Projection(), MAX_READ_EVENTS)
        setupEventStreams(RetryPolicy.noRetries())
    }

    @After
    fun cleanup() {
        if (repositoryFactory != null) {
            repositoryFactory!!.close()
            repositoryFactory = null
        }
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
    fun `does nothing when creating another stream using existing name if explicitly allowed`() {
        createWith(failOnExisting = CreateConflictStrategy.NOOP) { Event.ValueSet("the-value") }

        createWith(failOnExisting = CreateConflictStrategy.NOOP) { Event.ValueSet("the-value-2") }

        then assertState { value equals "the-value" }
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

        updateWith(ExpectedVersion.exactly(version)) {
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
            updateWith(ExpectedVersion.exactly(version)) {
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

        Thread.sleep(1000)
    }

    @Test
    fun `concurrent updates eventually succeed when retries configured`() {
        setupEventStreams(RetryPolicy(25, 50, 2))

        createWith { Event.ValueSet("the-value") }

        val slowUpdateStarted = ConcurrencyProgress("slow read")
        val sneakyUpdateCompleted = ConcurrencyProgress("sneaky update")

        concurrency.runInThread("slow update") {
            updateWith {
                slowUpdateStarted.happened()
                sneakyUpdateCompleted.await()
                Event.ValueSet("the-value-slow")
            }
        }

        concurrency.runInThread("sneaky update") {
            slowUpdateStarted.await()
            updateWith {
                Event.ValueSet("the-value-sneaky")
            }
            sneakyUpdateCompleted.happened()
        }

        Thread.sleep(1000)
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

        Thread.sleep(1000)
    }
    @Test
    fun `can load all events`() {
        val expected = mutableListOf<String>()

        fun appendRandomValue() {
            val value = Random.nextInt().toString()
            appendWith { Event.ValueSet(value) }
            expected += value
        }

        // Load just shy of a page full
        repeat(MAX_READ_EVENTS - 1) {
            appendRandomValue()
        }

        then assertState { allValues equals expected }

        // Load when exactly a page full
        appendRandomValue()

        then assertState { allValues equals expected }

        // Load when just over a page full
        appendRandomValue()

        then assertState { allValues equals expected }

        // Load with more than two pages full
        repeat(MAX_READ_EVENTS) {
            appendRandomValue()
        }

        then assertState { allValues equals expected }
    }

    private fun createWith(
            event: () -> Event
    ) = streams.create(randomId) { state -> state.apply(event) }

    private fun createWith(
            failOnExisting: CreateConflictStrategy,
            event: () -> Event
    ) = streams.create(randomId, failOnExisting) { state -> state.apply(event) }

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

    private fun setupEventStreams(retryPolicy: RetryPolicy) {
        streams = EventStreams(DefaultCommandFactory(aggregateRepository, retryPolicy))
    }

    class Projection : AggregateProjection<State, Event> {
        override fun apply(id: String, state: State, event: Event): State {
            return when (event) {
                is Event.ValueSet ->
                    state.copy(
                            allValues = state.allValues + event.value,
                            value = event.value
                    )
            }
        }

        override fun empty() = State()
    }

    data class State(
            val allValues: List<String> = listOf(),
            val value: String? = null,
    )

    private infix fun Any?.equals(other: Any?) {
        assertThat(this, equalTo(other))
    }

    @EventType(repositoryName = "testRepository")
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type",
            visible = true
    )
    @JsonSubTypes(JsonSubTypes.Type(Event.ValueSet::class))
    sealed class Event {
        @JsonTypeName("valueSet")
        data class ValueSet(val value: String) : Event()
    }

    private infix fun assertState(checks: State.() -> Unit) {
        val agg = aggregateRepository.load(randomId)
        val state = agg.state()
        state.checks()
    }

    companion object {
        private const val MAX_READ_EVENTS = 5
    }
}
