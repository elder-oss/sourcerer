package org.elder.sourcerer.subscription;

import org.elder.sourcerer.EventRecord;
import org.elder.sourcerer.EventRepository;
import org.elder.sourcerer.EventSubscriptionHandler;
import org.elder.sourcerer.EventSubscriptionPositionSource;
import org.elder.sourcerer.EventSubscriptionUpdate;
import org.elder.sourcerer.SubscriptionToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.subscriber.LambdaSubscriber;
import reactor.core.subscriber.Subscribers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class SubscriptionWorker<T> implements Runnable, SubscriptionToken {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionWorker.class);
    private final EventRepository<T> repository;
    private final EventSubscriptionPositionSource positionSource;
    private final EventSubscriptionHandler<T> handler;
    private final AtomicInteger retryCount;
    private final AtomicBoolean cancelled;
    private final Semaphore sleeper;
    private final int batchSize;
    private final int initialRetryDelayMillis;
    private final int maxRetryDelayMillis;
    private int subscriberCount;

    public SubscriptionWorker(
            final EventRepository<T> repository,
            final EventSubscriptionPositionSource positionSource,
            final EventSubscriptionHandler<T> handler,
            final int batchSize) {
        this.repository = repository;
        this.positionSource = positionSource;
        this.handler = handler;
        this.batchSize = batchSize;
        this.cancelled = new AtomicBoolean(false);
        this.retryCount = new AtomicInteger(0);
        this.sleeper = new Semaphore(0);
        this.initialRetryDelayMillis = 100;
        this.maxRetryDelayMillis = 30_000;
    }

    @Override
    public void run() {
        handler.subscriptionStarted(this); // If this dies, we die - fatal startup error!

        try {
            while (true) {
                try {
                    runOneSession();
                    // Clean return, can only mean we've reached the end ....
                    logger.info("Subscription stop acknowledge, thread terminating");
                    handler.subscriptionStopped();
                    return;
                } catch (Exception ex) {
                    logger.warn("Exception in subscription, retry logic will apply", ex);
                    boolean retry = handler.handleError(unwrapException(ex), retryCount.get());
                    if (retry) {
                        boolean keepGoing = sleepForRetry(retryCount.getAndIncrement());
                        if (!keepGoing) {
                            logger.debug("Asked to stop by sleeper, terminating thread");
                            return;
                        } else {
                            logger.info("Subscription restarting after error");
                            handler.subscriptionRestarting();
                        }
                    } else {
                        logger.warn("Subscription failed with terminal error", ex);
                        handler.subscriptionFailed(ex);
                        return;
                    }
                }
            }
        } catch (InterruptedException ex) {
            logger.warn("Interrupted processing subscription", ex);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        logger.info("Stopping subscription");
        cancelled.set(true);
        sleeper.release(Integer.MAX_VALUE);
    }

    private static Throwable unwrapException(final Exception ex) {
        if (ex instanceof DownstreamSubscriptionException) {
            return ex.getCause();
        } else {
            return ex;
        }
    }

    private void runOneSession() throws InterruptedException {
        subscriberCount++;
        Integer subscriptionPosition = positionSource.getSubscriptionPosition();
        BlockingQueue<Update<T>> currentUpdates =
                new ArrayBlockingQueue<>(batchSize);
        SessionSubscriber<T> subscriber =
                new SessionSubscriber<>(currentUpdates, "" + subscriberCount);
        LambdaSubscriber<EventSubscriptionUpdate<T>> boundedSubscriber = Subscribers.bounded(
                batchSize,
                subscriber::onNext,
                subscriber::onError,
                subscriber::onComplete);

        try {
            logger.info("Subscribing to event store ...");
            repository.getPublisher(subscriptionPosition).subscribe(boundedSubscriber);
            while (processUpdates(currentUpdates)) {
                logger.debug("Processed updates, will do more");
                retryCount.set(0);
            }
            // Clean exit, can only mean we're at the end of the subscription, or been explicitly
            // cancelled
            logger.info("Subscription worker finishing cleanly");
        } finally {
            logger.info("Subscription worker exiting");
            subscriber.kill();
            boundedSubscriber.dispose();
        }
    }

    private boolean processUpdates(final BlockingQueue<Update<T>> updatesQueue)
            throws InterruptedException {
        List<Update<T>> updateBatch = null;
        while (updateBatch == null) {
            if (cancelled.get()) {
                logger.info("Seen cancelled flag, bailing out");
                return false;
            }

            updateBatch = getUpdateBatch(updatesQueue);
        }

        // Updates can be a mix of events and control messages, we want to batch consecutive
        // event records, but need to handle control messages individually
        List<EventRecord<T>> events = null;
        for (Update<T> update : updateBatch) {
            switch (update.getUpdateType()) {
                case COMPLETED:
                    logger.debug("Subscription completed, processing pending updates");
                    processEventsIfAny(events);
                    logger.debug("Subscription completed, completing stream");
                    return false;
                case CAUGHT_UP:
                    logger.debug("Subscription caught up, processing pending events");
                    processEventsIfAny(events);
                    events = null;

                    logger.debug("Subscription caught up, signalling");
                    handler.subscriptionCaughtUp();
                    break;
                case EVENT:
                    if (events == null) {
                        events = new ArrayList<>();
                    }
                    events.add(update.getEvent());
                    break;
                case ERROR:
                    logger.debug("Subscription error, processing pending updates");
                    processEventsIfAny(events);
                    logger.debug("Subscription error, signalling", update.getError());
                    throw new DownstreamSubscriptionException(update.getError());
                default:
                    throw new IllegalArgumentException("Unknown append type");
            }
        }

        processEventsIfAny(events);
        return true;
    }

    private void processEventsIfAny(final List<EventRecord<T>> events) {
        if (events != null && !events.isEmpty()) {
            handler.processEvents(events);
        }
    }

    private List<Update<T>> getUpdateBatch(
            final BlockingQueue<Update<T>> updatesQueue) throws InterruptedException {
        Update<T> update = updatesQueue.poll(1000, TimeUnit.MILLISECONDS);
        if (update != null) {
            // We have at least one pending append, check if there's more!
            List<Update<T>> updatesBatch = new ArrayList<>();
            updatesBatch.add(update);
            if (updatesQueue.peek() != null) {
                logger.debug("Subscription received append, queue not empty, draining ...");
                updatesQueue.drainTo(updatesBatch);
            } else {
                logger.debug("Subscription received single append");
            }
            return updatesBatch;
        } else {
            // Nothing pending, nothing to see here
            logger.trace("No append (yet)");
            return null;
        }
    }

    private boolean sleepForRetry(final int attempts) throws InterruptedException {
        long delayMillis = getCurrentRetryInterval(attempts);

        logger.info("Sleeping for {} millis before retrying subscription", delayMillis);
        sleeper.tryAcquire(delayMillis, TimeUnit.MILLISECONDS);
        return !cancelled.get();
    }

    private long getCurrentRetryInterval(final int attempts) {
        // This would be a simple shift, but shift would overflow ...
        long delay = initialRetryDelayMillis;
        for (int i = 0; i < attempts; i++) {
            delay <<= 1;
            if (delay > maxRetryDelayMillis) {
                return maxRetryDelayMillis;
            }
        }
        return delay;
    }
}
