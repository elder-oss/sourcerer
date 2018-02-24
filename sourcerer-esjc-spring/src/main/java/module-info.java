module org.elder.sourcerer.esjc.spring {
    requires transitive org.elder.sourcerer.esjc;
    requires org.slf4j;
    requires spring.boot.actuator;
    requires spring.context;
    requires spring.beans;
    requires esjc;
    requires com.fasterxml.jackson.databind;
    exports org.elder.sourcerer.esjc.spring;
}
