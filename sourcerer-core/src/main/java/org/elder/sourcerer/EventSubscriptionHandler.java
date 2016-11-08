package org.elder.sourcerer;

import java.util.List;

/**
 * Responsible for providing the logic for processing event from an event stream (which may be a
 * combination of more than one individual stream.
 * <p>
 * The event subscription handler must be callable from multiple threads, but will only have a
 * single method called at a time.
 *
 * @param <T> The type of events being processed by this handler.
 */
public interface EventSubscriptionHandler<T> {
    /**
     * Called once when the subscription is initially started. This method is guaranteed to be
     * called before processEvent(), but will not be called on retries.
     *
     * @param subscriptionToken An instance of closeable that can be called by the subscription
     *                          handler (or other component) to terminate the subscription.
     */
    void subscriptionStarted(SubscriptionToken subscriptionToken);

    /**
     * Called after retries before additional calls to processEvent(). Subscription handler can use
     * this method to reset any internal state or connections to other systems as an error causing a
     * retry to be attempted may have originated from a bade state of the handler.
     */
    void subscriptionRestarting();

    /**
     * The subscription has been caught up with what's in the underlying store, i.e. new updates
     * following this control message are from live updates, rather than historical replays. Note
     * that this method may be called multiple time, as a subscription falls behind (for example
     * after network reconnect) and then catches up again.
     */
    void subscriptionCaughtUp();

    /**
     * Called after the subscription has failed fatally with an error. This method is not called on
     * retries, only after the subscription is determined to be terminated with an error.
     */
    void subscriptionFailed(Throwable error);

    /**
     * Called after the subscription has gracefully stopped, allowing the instance to clean up any
     * resources held in relation to the subscription.
     */
    void subscriptionStopped();

    /**
     * Process a batch of events. This method may be invoked with one or more events in the order
     * they were received, i.e. with the first item being the oldest. If the method returns
     * successfully, all events are assumed to have been successfully processed. On errors, all
     * events may be retried.
     *
     * @param eventRecords The new events to process.
     */
    void processEvents(List<EventRecord<T>> eventRecords);

    /**
     * Called when a problem occurs related to the subscription, either due to an underlying IO
     * error such as broken network connection, or from logic processing the event including but not
     * limited to from the processEvent handler in this interface.
     *
     * @param error      The error that occurred on the subscription.
     * @param retryCount The number of times retries have already been attempted for this step of
     *                   the subscription, set to 1 the first time a retry is attempted.
     * @return True if the subscription should continue, false to declare this a terminal error for
     * the subscription and stop further processing. If true is returned, the subscription may be
     * restarted and already processed messages may be replayed.
     */
    boolean handleError(Throwable error, int retryCount);
}
