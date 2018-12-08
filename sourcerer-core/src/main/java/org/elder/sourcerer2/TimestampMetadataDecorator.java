package org.elder.sourcerer2;

import com.google.common.collect.ImmutableMap;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Map;

@Named
@Singleton
public class TimestampMetadataDecorator implements MetadataDecorator {
    private static final String TIMESTAMP_KEY = "timestamp";

    @Override
    public Map<String, String> getMetadata() {
        return ImmutableMap.of(TIMESTAMP_KEY, Instant.now().toString());
    }

    public static Instant getTimestamp(final Map<String, String> metadata) {
        String timestampStr = metadata.get(TIMESTAMP_KEY);
        return timestampStr == null ? null : Instant.parse(timestampStr);
    }
}
