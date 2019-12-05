package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.EventNormalizer

/**
 * Information about a particular category (e.g. related family of streams, often representing a type of aggregate
 * e.g. a customer).
 */
data class DbstoreRepositoryInfo<T>(
        /**
         * The base Java type for events in this repository.
         */
        val eventType: Class<T>,

        /**
         * The namespace used by this category. Namespaces can be used to logically segment a data store for use with
         * separate environments or test runs.
         */
        val namespace: String,

        /**
         * The name of the repository. This should be unique across all categories in the same namespace.
         */
        val repository: String,

        /**
         * An optional normalizer for the repository. The normalizer is used for just-in-time transformations of
         * events as they are read from the event store, for compatibility and data fix-up.
         */
        val normalizer: EventNormalizer<T>?
)
