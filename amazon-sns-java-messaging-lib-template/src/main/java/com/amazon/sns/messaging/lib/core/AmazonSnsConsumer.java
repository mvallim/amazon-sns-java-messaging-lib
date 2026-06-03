package com.amazon.sns.messaging.lib.core;

import java.util.concurrent.CompletableFuture;

/**
 * Consumer interface for Amazon SNS messaging. Implementations handle batch publishing
 * of requests and dispatching of responses or errors to pending request futures.
 *
 * @param <R> the publish batch request type
 * @param <O> the publish batch result type
 */
public interface AmazonSnsConsumer<R, O> {

  /**
   * Publishes a batch request to Amazon SNS.
   *
   * @param publishBatchRequest the batch request to publish
   * @return the publish result
   */
  public abstract O publish(final R publishBatchRequest);

  /**
   * Handles an error that occurred during publishing.
   *
   * @param publishBatchRequest the batch request that failed
   * @param throwable           the exception that was thrown
   */
  public void handleError(final R publishBatchRequest, final Throwable throwable);

  /**
   * Handles the response from a successful publish call.
   *
   * @param publishBatchResult the result of the publish operation
   */
  public void handleResponse(final O publishBatchResult);

  /**
   * Shuts down the consumer, waiting up to 60 seconds for both the scheduled and
   * worker executor services to terminate.
   */
  public void shutdown();

  /**
   * Returns a {@link CompletableFuture} that completes once all pending requests
   * have been processed (i.e., both the pending requests map and the topic
   * requests queue are empty).
   *
   * @return a future that completes when all requests are drained
   */
  public CompletableFuture<Void> await();

}
