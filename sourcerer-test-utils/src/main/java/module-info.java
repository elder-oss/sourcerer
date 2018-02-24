module org.elder.sourcerer.test {
    requires org.elder.sourcerer.core;
    requires junit;
    requires org.slf4j;
    requires tocker.core;
    requires unirest.java;
    exports org.elder.sourcerer.test.data;
    exports org.elder.sourcerer.test.rules;
}
