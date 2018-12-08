package org.elder.sourcerer2;

/**
 * Responsible for retrieving the current position of a logical subscription. The event subscription
 * position source (if one is provided) is queried when subscriptions are started to know where to
 * resume an existing subscription from.
 * <p>
 * The event subscription position source may also be queried when a subscription is resumed due to
 * network disconnects or other intermittent failures.
 * <p>
 * Implementations may either track progress explicitly, e.g. by periodically storing the current
 * position, or implicitly, by deducing the current position from materialized data.
 */
@FunctionalInterface
public interface EventSubscriptionPositionSource {
    /**
     * Gets the current position to start or resume a subscription from.
     *
     * @return The numerical stream position / version to start or resume a subscription from,
     * exclusive. This would normally be the version of the last event that was successfully handled
     * by the subscription. Use null to start the subscription from the beginning.
     */
    Integer getSubscriptionPosition();
}
