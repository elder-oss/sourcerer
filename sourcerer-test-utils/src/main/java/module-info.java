module org.elder.sourcerer.test {
    exports org.elder.sourcerer.test.data;
    exports org.elder.sourcerer.test.rules;
    requires org.elder.sourcerer;
    requires junit;
    requires org.slf4j;
    requires tocker.core;
    requires unirest.java;
}
