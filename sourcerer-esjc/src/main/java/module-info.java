module org.elder.sourcerer.esjc {
    exports org.elder.sourcerer.esjc;
    requires annotations;
    requires org.slf4j;
    requires com.google.common;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires org.reactivestreams;
    requires reactor.core;
    requires esjc;
    requires org.elder.sourcerer;
    requires org.elder.sourcerer.utils;
}
