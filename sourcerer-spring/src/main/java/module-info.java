module org.elder.sourcerer.eventstore.spring {
    exports org.elder.sourcerer.spring;
    requires transitive org.elder.sourcerer;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires spring.beans;
    requires spring.context;
}
