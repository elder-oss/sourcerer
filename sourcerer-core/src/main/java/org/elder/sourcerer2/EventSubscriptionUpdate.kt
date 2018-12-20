package org.elder.sourcerer2

sealed class EventSubscriptionUpdate<T> {
    class CaughtUp<T> : EventSubscriptionUpdate<T>()
    data class Event<T>(val event: EventRecord<T>): EventSubscriptionUpdate<T>()

    companion object {
        @JvmStatic
        fun <T> caughtUp(): EventSubscriptionUpdate<T> {
            return CaughtUp()
        }

        @JvmStatic
        fun <T> ofEvent(event: EventRecord<T>): EventSubscriptionUpdate<T> {
            return Event(event)
        }
    }
}
