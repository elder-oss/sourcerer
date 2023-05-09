package org.elder.sourcerer.kotlin

import org.elder.sourcerer.AggregateProjection

open class SnapshotProjection<State>(
    private val clazz: Class<State>
) : AggregateProjection<State, SnapshotEvent> {
    override fun apply(
        id: String,
        state: State,
        event: SnapshotEvent
    ): State {
        return when (event) {
            is SnapshotEvent.Added<*> -> {
                event.snapshot.state as State
            }
        }
    }

    override fun empty() = clazz.newInstance()
}