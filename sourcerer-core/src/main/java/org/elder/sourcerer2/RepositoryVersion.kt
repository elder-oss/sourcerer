package org.elder.sourcerer2

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Represents the position of events within a repository (collection of event streams).
 * The repository version is only guaranteed to be usable on the same context as it was originally
 * created from, e.g. a global subscription version may be be used in a meaningful way in the
 * context of subscriptions to a particular event category.
 *
 * The value of the version string itself is dependent on the storage driver and should be treated
 * as opaque, but will always have a natural order representing the order in which events were
 * created, e.g. when comparing either the stream version object or the string it wraps, more recent
 * events will have a "higher" value than older ones.
 */
data class RepositoryVersion @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
        /**
         * The raw version id, to be treated as opaque, but exposed for where a raw string
         * needs to be persisted.
         */
        @get:JsonValue
        val version: String
) : Comparable<RepositoryVersion> {
    override fun compareTo(other: RepositoryVersion): Int {
        return version.compareTo(other.version)
    }

    companion object {
        @JvmStatic
        fun ofString(version: String): RepositoryVersion {
            return RepositoryVersion(version)
        }

        /**
         * Some implementations use integers to represent a version. This translates an integer to
         * a string form that will always follow the expected ordering for string comparison
         * by 0 padding.
         */
        @JvmStatic
        fun ofInt(version: Int): RepositoryVersion {
            return RepositoryVersion(version.toString().padStart(INT_MAX_DIGITS, '0'))
        }

        /**
         * Some implementations use integers to represent a version. This translates an integer to
         * a string form that will always follow the expected ordering for string comparison
         * by 0 padding.
         */
        @JvmStatic
        fun ofLong(version: Long): RepositoryVersion {
            return RepositoryVersion(version.toString().padStart(LONG_MAX_DIGITS, '0'))
        }

        private const val INT_MAX_DIGITS = Int.MAX_VALUE.toString().length
        private const val LONG_MAX_DIGITS = Long.MAX_VALUE.toString().length
    }
}
