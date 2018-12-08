package org.elder.sourcerer2.utils;

import java.util.Collection;

public final class ElderPreconditions {
    private ElderPreconditions() {
    }

    public static <T> void checkNotEmpty(final Collection<T> collection) {
        if (collection.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty collection provided where non empty was expected");
        }
    }

    public static void checkNotEmpty(final String str) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty string provided where non empty was expected");
        }
    }
}
