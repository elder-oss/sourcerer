package org.elder.sourcerer2.esjc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.msemys.esjc.EventStore;
import com.github.msemys.esjc.EventStoreBuilder;
import com.google.common.collect.ImmutableMap;
import org.elder.sourcerer2.EventData;
import org.elder.sourcerer2.EventId;
import org.elder.sourcerer2.EventRecord;
import org.elder.sourcerer2.EventRepository;
import org.elder.sourcerer2.ExpectedVersion;
import org.elder.sourcerer2.StreamId;
import org.elder.sourcerer2.eventstore.test.data.TestEventType;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class EventStoreEsjcEventRepositoryIntegrationTest {
    private static final String NAMESPACE = randomNamespace();

    private static EventRepository<TestEventType> repository;
    private static EventStore eventStore;

    private final StreamId streamId = randomStreamId();

    @BeforeClass
    public static void setup() {
        eventStore = EventStoreBuilder
                .newBuilder()
                .userCredentials("admin", "changeit")
                .requireMaster(false)
                .failOnNoServerResponse(true)
                .singleNodeAddress("127.0.0.1", 1113)
                .build();

        eventStore.connect();

        EventStoreEsjcEventRepositoryFactory factory = new EventStoreEsjcEventRepositoryFactory(
                eventStore,
                new ObjectMapper(),
                NAMESPACE);
        repository = factory.getEventRepository(TestEventType.class);
    }

    @AfterClass
    public static void teardown() {
        eventStore.disconnect();
    }

    @Test
    public void readFirstEvent() {
        append(new TestEventType("payload-1"));
        append(new TestEventType("payload-2"));

        EventRecord<TestEventType> actual = repository.readFirst(streamId);

        assertThat(actual.getEvent().getValue(), equalTo("payload-1"));
    }

    @Test
    public void readLastEvent() {
        append(new TestEventType("payload-1"));
        append(new TestEventType("payload-2"));

        EventRecord<TestEventType> actual = repository.readLast(streamId);

        assertThat(actual.getEvent().getValue(), equalTo("payload-2"));
    }

    @Test
    public void readLastReturnsNullForMissingStream() {
        EventRecord<TestEventType> actual = repository.readLast(streamId);

        assertThat(actual, nullValue());
    }

    @Test
    public void readFirstReturnsNullForMissingStream() {
        EventRecord<TestEventType> actual = repository.readFirst(streamId);

        assertThat(actual, nullValue());
    }

    private void append(final TestEventType type) {
        EventData<TestEventType> events = eventData(type);
        repository.append(streamId, Collections.singletonList(events), ExpectedVersion.any());
    }

    @NotNull
    private EventData<TestEventType> eventData(final TestEventType event) {
        return new EventData<>(
                EventId.newUniqueId(),
                "type",
                ImmutableMap.of(),
                event);
    }

    @NotNull
    private static String randomNamespace() {
        return "test_" + UUID.randomUUID().toString().replaceAll("-", "_");
    }

    private static StreamId randomStreamId() {
        return StreamId.ofString(String.format("%d", new Random().nextInt(100000)));
    }
}
