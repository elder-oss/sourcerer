module org.elder.sourcerer.esjc {
    requires transitive org.elder.sourcerer.core;
    requires org.elder.sourcerer.utils;
    requires esjc;
    requires org.slf4j;
    requires com.google.common;
    requires com.fasterxml.jackson.databind;
    exports org.elder.sourcerer.esjc;
}
