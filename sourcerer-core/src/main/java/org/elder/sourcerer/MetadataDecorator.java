package org.elder.sourcerer;

import java.util.Map;

/**
 * Implemented by decorators able of appending metadata to events based on some context of the
 * environment, e.g. currently logged in user, details of the server itself etc.
 */
public interface MetadataDecorator {
    Map<String, String> getMetadata();
}
