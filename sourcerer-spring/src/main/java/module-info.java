module org.elder.sourcerer.eventstore.spring {
    requires transitive org.elder.sourcerer.core;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires spring.beans;
    requires spring.context;
    exports org.elder.sourcerer.spring;
}
