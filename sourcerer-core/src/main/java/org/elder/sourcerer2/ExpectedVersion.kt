package org.elder.sourcerer2

import org.elder.sourcerer2.exceptions.ConflictingExpectedVersionsException

/**
 * Describes the version that an aggregate is expected to be in when read, or appended to.
 */
sealed class ExpectedVersion {
    data class Exactly(val streamVersion: StreamVersion) : ExpectedVersion()
    object NotCreated : ExpectedVersion() {
        override fun toString(): String {
            return "ExpectedVersion.NotCreated"
        }
    }
    object AnyExisting : ExpectedVersion()  {
        override fun toString(): String {
            return "ExpectedVersion.AnyExisting"
        }
    }
    object Any : ExpectedVersion()  {
        override fun toString(): String {
            return "ExpectedVersion.Any"
        }
    }

    companion object {
        // Java convenience methods.
        @JvmStatic
        fun any(): ExpectedVersion {
            return ExpectedVersion.Any
        }

        @JvmStatic
        fun anyExisting(): ExpectedVersion {
            return ExpectedVersion.AnyExisting
        }

        @JvmStatic
        fun notCreated(): ExpectedVersion {
            return ExpectedVersion.NotCreated
        }

        @JvmStatic
        fun exactly(version: StreamVersion): ExpectedVersion {
            return ExpectedVersion.Exactly(version)
        }

        @JvmStatic
        fun merge(
                baseVersion: ExpectedVersion?,
                newVersion: ExpectedVersion?
        ): ExpectedVersion {
            return if (baseVersion != null && newVersion != null) {
                if (baseVersion.ordinal() > newVersion.ordinal()) {
                    mergeOrdered(newVersion, baseVersion)
                } else {
                    mergeOrdered(baseVersion, newVersion)
                }
            } else baseVersion ?: (newVersion ?: ExpectedVersion.any())
        }

        /**
         * Assumes the expected versions are given in ordinal order.
         */
        private fun mergeOrdered(
                baseVersion: ExpectedVersion,
                newVersion: ExpectedVersion
        ): ExpectedVersion {
            return when (baseVersion) {
                ExpectedVersion.Any ->
                    // Any is always compatible with other expected version
                    newVersion
                ExpectedVersion.AnyExisting -> when (newVersion) {
                    ExpectedVersion.AnyExisting,
                    ExpectedVersion.Any -> ExpectedVersion.anyExisting()
                    ExpectedVersion.NotCreated -> throw ConflictingExpectedVersionsException(
                            "Cannot merge 'any existing' with 'not created'",
                            baseVersion, newVersion)
                    is ExpectedVersion.Exactly -> newVersion
                }
                is ExpectedVersion.Exactly -> when (newVersion) {
                    is ExpectedVersion.Exactly -> {
                        if (baseVersion.streamVersion != newVersion.streamVersion) {
                            throw ConflictingExpectedVersionsException(
                                    "Cannot merge 'exactly "
                                            + baseVersion.streamVersion
                                            + "' with 'exactly "
                                            + newVersion.streamVersion
                                            + "'",
                                    baseVersion, newVersion)
                        }
                        baseVersion
                    }
                    ExpectedVersion.NotCreated ->
                        throw ConflictingExpectedVersionsException(
                                "Cannot merge 'exactly "
                                        + baseVersion.streamVersion
                                        + "' with 'not created'",
                                baseVersion, newVersion)
                    else ->
                        throw RuntimeException(
                                "Internal error, unexpected version combination merging "
                                        + baseVersion
                                        + " with " + newVersion)

                }
                ExpectedVersion.NotCreated -> when (newVersion) {
                    ExpectedVersion.NotCreated -> baseVersion
                    else ->
                        throw RuntimeException(
                                "Internal error, unexpected version combination merging "
                                        + baseVersion
                                        + " with " + newVersion)
                }
            }
        }
    }
}

private fun ExpectedVersion.ordinal(): Int {
    return when (this) {
        ExpectedVersion.Any -> 0
        ExpectedVersion.AnyExisting -> 1
        is ExpectedVersion.Exactly -> 2
        ExpectedVersion.NotCreated -> 3
    }
}
