package org.elder.sourcerer2.subscription;

import org.elder.sourcerer2.EventSubscriptionUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SessionSubscriber<T> {
    private static final Logger logger = LoggerFactory.getLogger(SessionSubscriber.class);
    private final BlockingQueue<Update<T>> updateQueue;
    private final String label;
    private final AtomicBoolean cancelled;

    public SessionSubscriber(
            final BlockingQueue<Update<T>> updateQueue,
            final String label) {
        this.updateQueue = updateQueue;
        this.label = label;
        this.cancelled = new AtomicBoolean(false);
    }

    public void kill() {
        logger.info("[{}] Cancelling session subscriber", label);
        cancelled.set(true);
    }

    public void onNext(final EventSubscriptionUpdate<T> update) {
        try {
            switch (update.getUpdateType()) {
                case EVENT:
                    logger.debug("Offering event {}", update.getEvent());
                    tryOffer(Update.createEvent(update.getEvent()));
                    break;
                case CAUGHT_UP:
                    logger.debug("Offering caught up");
                    tryOffer(Update.createCaughtUp());
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unrecognized event update type: " + update.getUpdateType());
            }
        } catch (InterruptedException ex) {
            logger.warn("Session subscriber thread interrupted", ex);
            Thread.currentThread().interrupt();
        }
    }

    public void onError(final Throwable error) {
        try {
            logger.debug("Offering error: {}", error.toString());
            tryOffer(Update.createError(error));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public void onComplete() {
        try {
            logger.debug("Offering completion");
            tryOffer(Update.createCompleted());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void tryOffer(final Update<T> update) throws InterruptedException {
        while (true) {
            if (cancelled.get()) {
                logger.debug("[{}] Session subscriber killed, ignoring incoming", label);
                return;
            }

            boolean success = updateQueue.offer(update, 1000, TimeUnit.MILLISECONDS);
            if (success) {
                return;
            }
            logger.warn(
                    "[{}] SLOW SUBSCRIBER! Time out offering new items, will retry",
                    label);
        }
    }
}
