package org.elder.sourcerer2.subscription;

import org.elder.sourcerer2.EventRecord;
import org.elder.sourcerer2.EventSubscriptionHandler;
import org.elder.sourcerer2.SubscriptionToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AbstractSubscriptionHandler<T> implements EventSubscriptionHandler<T> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSubscriptionHandler.class);

    @Override
    public void subscriptionStarted(final SubscriptionToken subscriptionToken) {
        logger.debug("Subscription started");
    }

    @Override
    public void subscriptionRestarting() {
        logger.debug("Subscription restarting");
    }

    @Override
    public void subscriptionFailed(final Throwable error) {
        logger.debug("Subscription failed", error);
    }

    @Override
    public void subscriptionStopped() {
        logger.debug("Subscription stopped");
    }

    @Override
    public void subscriptionCaughtUp() {
        logger.debug("Subscription has caught up with historical events...");
    }

    @Override
    public void processEvents(final List<EventRecord<T>> eventRecords) {
        logger.debug("Subscription saw {} events", eventRecords.size());
    }

    @Override
    public boolean handleError(final Throwable error, final int retryCount) {
        logger.debug(
                "Subscription not handling error {} after {} attempts",
                error.getMessage(),
                retryCount);
        return false;
    }
}
