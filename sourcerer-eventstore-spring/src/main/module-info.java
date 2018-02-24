module org.elder.sourcerer.eventstore.spring {
    requires transitive org.elder.sourcerer.eventstore;
    requires slf4j.api;
    requires esjc;
    requires spring.boot.actuator;
    exports org.elder.sourcerer.eventstore.spring;
}
