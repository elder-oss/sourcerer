// CHECKSTYLE:OFF
module org.elder.sourcerer.core {
    requires javax.inject;
    requires annotations;
    requires org.slf4j;
    requires reactor.core;
    requires com.google.common;
    exports org.elder.sourcerer;
    exports org.elder.sourcerer.exceptions;
    exports org.elder.sourcerer.functions;
}
