package org.elder.sourcerer.eventstoredb;

import com.eventstore.dbclient.Endpoint;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elder.sourcerer.EventData;
import org.elder.sourcerer.EventRecord;
import org.elder.sourcerer.EventRepository;
import org.elder.sourcerer.ExpectedVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class EventStoreGrpcEventRepositoryIntegrationTest {
    private static final String NAMESPACE = randomNamespace();

    private static EventRepository<TestEventType> repository;
    private static EventStoreDBClient eventStore;

    private final String streamId = randomStreamId();

    @BeforeClass
    public static void setup() {
        EventStoreDBClientSettings settings = EventStoreDBClientSettings.builder()
                .addHost(new Endpoint("127.0.0.1", 2113))
                .defaultCredentials("admin", "changeit")
                .throwOnAppendFailure(true)
                .tls(false)
                .keepAliveInterval(10000)
                .buildConnectionSettings();

        eventStore = EventStoreDBClient.create(settings);

        EventStoreGrpcEventRepositoryFactory factory = new EventStoreGrpcEventRepositoryFactory(
                eventStore,
                new ObjectMapper(),
                NAMESPACE);
        repository = factory.getEventRepository(TestEventType.class);
    }

    @AfterClass
    public static void teardown() {
        try {
            eventStore.shutdown();
        } catch (ExecutionException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
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
    private EventData<TestEventType> eventData(final TestEventType type) {
        return new EventData<>("type", UUID.randomUUID(), new HashMap<>(), type);
    }

    @NotNull
    private static String randomNamespace() {
        return "test_" + UUID.randomUUID().toString().replaceAll("-", "_");
    }

    private static String randomStreamId() {
        return String.format("%d", new Random().nextInt(100000));
    }
}
