module org.elder.sourcerer.core {
    requires slf4j.api;
    requires com.google.common;
    exports org.elder.sourcerer;
    exports org.elder.sourcerer.exceptions;
    exports org.elder.sourcerer.functions;
    exports org.elder.sourcerer.utils to org.elder.sourcerer.esjc;
}
