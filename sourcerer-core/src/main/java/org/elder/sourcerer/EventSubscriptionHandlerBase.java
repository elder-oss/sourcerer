package org.elder.sourcerer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class simplifying the implementation of EventSubscriptionHandler with default behavior for
 * all methods except processEvent().
 */
public abstract class EventSubscriptionHandlerBase<T> implements EventSubscriptionHandler<T> {
    private static final Logger DEFAULT_LOGGER
            = LoggerFactory.getLogger(EventSubscriptionHandlerBase.class.getName());
    private AtomicReference<SubscriptionToken> subscriptionToken;
    private Logger logger;

    protected EventSubscriptionHandlerBase(final Logger logger) {
        this.logger = logger;
        this.subscriptionToken = new AtomicReference<>(null);
    }

    protected EventSubscriptionHandlerBase() {
        this(DEFAULT_LOGGER);
    }

    @Override
    public void subscriptionStarted(final SubscriptionToken subscriptionToken) {
        logger.info("Subscription started");
        this.subscriptionToken.set(subscriptionToken);
    }

    @Override
    public void subscriptionRestarting() {
        logger.warn("Subscription restarted");
    }

    @Override
    public void subscriptionFailed(final Throwable error) {
        logger.error("Subscription failed", error);
    }

    @Override
    public void subscriptionStopped() {
        logger.info("Subscription terminated cleanly");
    }

    @Override
    public void subscriptionCaughtUp() {
        logger.debug("Subscription has caught up with historical events...");
    }

    @Override
    public boolean handleError(final Throwable ex, final int retryCount) {
        logger.warn("Error in subscription after " + retryCount + " attempts, restarting", ex);
        return true;
    }

    public void closeSubscripton() {
        logger.debug("Closing subscription");
        SubscriptionToken token = subscriptionToken.get();
        if (token != null) {
            token.stop();
            logger.debug("Subscription closed");
        } else {
            throw new IllegalStateException("Unable to stop subscription that has not yet started");
        }
    }
}
