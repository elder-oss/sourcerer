module org.elder.sourcerer {
    exports org.elder.sourcerer;
    exports org.elder.sourcerer.exceptions;
    exports org.elder.sourcerer.functions;
    requires annotations;
    requires javax.inject;
    requires org.slf4j;
    requires reactor.core;
    requires com.google.common;
}
