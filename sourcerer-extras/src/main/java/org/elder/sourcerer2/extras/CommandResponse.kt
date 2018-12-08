package org.elder.sourcerer2.extras

import org.elder.sourcerer2.CommandResult
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.StreamVersion

data class CommandResponse(
        val id: StreamId,
        val newVersion: StreamVersion?,
        /**
         * True if the operation was a no-op, i.e. no modification was made.
         */
        val noOp: Boolean
) {
    companion object {
        fun of(result: CommandResult<*>): CommandResponse {
            return CommandResponse(
                    result.aggregateId,
                    result.newVersion,
                    result.events == null || result.events.isEmpty())
        }
    }
}
