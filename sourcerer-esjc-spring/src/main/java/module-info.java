module org.elder.sourcerer.esjc.spring {
    requires transitive org.elder.sourcerer.esjc;
    requires spring.boot.actuator;
    requires esjc;
    requires slf4j.api;
    exports org.elder.sourcerer.esjc.spring;
}
