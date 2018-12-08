package org.elder.sourcerer2.eventstore;

import akka.actor.ActorSystem;
import com.fasterxml.jackson.databind.ObjectMapper;
import eventstore.Settings;
import eventstore.j.EsConnection;
import eventstore.j.EsConnectionFactory;
import org.elder.sourcerer2.EventData;
import org.elder.sourcerer2.EventRecord;
import org.elder.sourcerer2.EventRepository;
import org.elder.sourcerer2.ExpectedVersion;
import org.elder.sourcerer2.eventstore.test.data.TestEventType;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.Option;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class EventStoreEventRepositoryIntegrationTest {
    private static final String NAMESPACE = randomNamespace();

    private static EventRepository<TestEventType> repository;

    private final String streamId = randomStreamId();

    @BeforeClass
    public static void setup() {
        ActorSystem system = ActorSystem.create();
        Settings defaultSettings = Settings.Default();

        Settings settings = new Settings(
                new InetSocketAddress("127.0.0.1", 1113),
                defaultSettings.connectionTimeout(),
                -1,
                defaultSettings.reconnectionDelayMin(),
                defaultSettings.reconnectionDelayMax(),
                defaultSettings.defaultCredentials(),
                defaultSettings.heartbeatInterval(),
                defaultSettings.heartbeatTimeout(),
                defaultSettings.operationMaxRetries(),
                defaultSettings.operationTimeout(),
                defaultSettings.resolveLinkTos(),
                false,
                defaultSettings.readBatchSize(),
                defaultSettings.backpressure(),
                Option.empty());
        EsConnection esConnection = EsConnectionFactory.create(system, settings);

        repository = new EventStoreEventRepositoryFactory(
                esConnection,
                new ObjectMapper(),
                NAMESPACE)
                .getEventRepository(TestEventType.class);
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

    private String randomStreamId() {
        return String.format("%d", new Random().nextInt(100000));
    }
}
