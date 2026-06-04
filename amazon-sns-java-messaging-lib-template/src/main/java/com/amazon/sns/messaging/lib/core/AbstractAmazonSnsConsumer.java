/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.sns.messaging.lib.core;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sns.messaging.lib.concurrent.ThreadFactoryProvider;
import com.amazon.sns.messaging.lib.core.RequestEntryInternalFactory.RequestEntryInternal;
import com.amazon.sns.messaging.lib.exception.MaximumAllowedMessageException;
import com.amazon.sns.messaging.lib.model.PublishRequestBuilder;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;

// @formatter:off
/**
 * Abstract base class for Amazon SNS message consumers. Periodically drains a
 * {@link BlockingQueue} of {@link RequestEntry} items, batches them, and publishes
 * them to SNS. Subclasses implement the actual publish and response handling logic.
 *
 * @param <C> the Amazon SNS client type
 * @param <R> the publish batch request type
 * @param <O> the publish batch result type
 * @param <E> the request entry payload type
 */
abstract class AbstractAmazonSnsConsumer<C, R, O, E> implements Runnable, AmazonSnsConsumer<R, O> {

  /**
   * Kilobyte constant used for size calculations.
   */
  private static final Integer KB = 1024;

  /**
   * Maximum batch size threshold of 256 KB imposed by Amazon SNS.
   */
  private static final Integer BATCH_SIZE_BYTES_THRESHOLD = 256 * AbstractAmazonSnsConsumer.KB;

  /** Class logger. */
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAmazonSnsConsumer.class);

  /** Single-thread scheduler that periodically triggers batch draining. */
  private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(ThreadFactoryProvider.getThreadFactory());

  /** The Amazon SNS client used for publishing batches. */
  protected final C amazonSnsClient;

  /** The topic configuration properties. */
  private final TopicProperty topicProperty;

  /** Factory for creating internal request entry representations. */
  private final RequestEntryInternalFactory requestEntryInternalFactory;

  /** Shared map of pending requests keyed by request ID for async completion. */
  protected final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests;

  /** The blocking queue that buffers incoming topic requests. */
  private final BlockingQueue<RequestEntry<E>> topicRequests;

  /** Optional decorator applied to the publish batch request before sending. */
  private final UnaryOperator<R> publishDecorator;

  /** Executor service for asynchronous (non-FIFO) publishing. */
  private final ExecutorService executorService;

  /**
   * Creates a new abstract consumer.
   *
   * @param amazonSnsClient  the Amazon SNS client
   * @param topicProperty    the topic configuration
   * @param objectMapper     the Jackson ObjectMapper for payload serialization
   * @param pendingRequests  the shared map of pending requests keyed by request ID
   * @param topicRequests    the shared blocking queue for topic requests
   * @param executorService  the executor service for async publishing
   * @param publishDecorator a decorator for the publish batch request
   */
  protected AbstractAmazonSnsConsumer(
      final C amazonSnsClient,
      final TopicProperty topicProperty,
      final ObjectMapper objectMapper,
      final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests,
      final BlockingQueue<RequestEntry<E>> topicRequests,
      final ExecutorService executorService,
      final UnaryOperator<R> publishDecorator) {

    this.amazonSnsClient = amazonSnsClient;
    this.topicProperty = topicProperty;
    requestEntryInternalFactory = new RequestEntryInternalFactory(objectMapper);
    this.pendingRequests = pendingRequests;
    this.topicRequests = topicRequests;
    this.publishDecorator = publishDecorator;
    this.executorService = executorService;

    scheduledExecutorService.scheduleAtFixedRate(this, 0, topicProperty.getLinger(), TimeUnit.MILLISECONDS);
  }

  /**
   * Returns a factory function that creates a publish batch request from a topic ARN
   * and a list of internal request entries.
   *
   * @return a bi-function mapping (topicArn, entries) to a publish batch request
   */
  protected abstract BiFunction<String, List<RequestEntryInternal>, R> supplierPublishRequest();

  /**
   * Performs the actual publish call and dispatches the response or error.
   *
   * @param publishBatchRequest the batch request to publish
   */
  private void doPublish(final R publishBatchRequest) {
    try {
      handleResponse(publish(publishDecorator.apply(publishBatchRequest)));
    } catch (final Exception ex) {
      handleError(publishBatchRequest, ex);
    }
  }

  /**
   * Publishes a batch either synchronously (FIFO) or asynchronously (standard).
   *
   * @param publishBatchRequest the batch request to publish
   */
  private void publishBatch(final R publishBatchRequest) {
    try {
      final Runnable runnable = () -> doPublish(publishBatchRequest);

      if (topicProperty.isFifo()) {
        runnable.run();
      } else {
        CompletableFuture.runAsync(runnable, executorService);
      }
    } catch (final Exception ex) {
      handleError(publishBatchRequest, ex);
    }
  }

  /**
   * Periodically drains the request queue and publishes batches.
   */
  @Override
  @SneakyThrows
  public void run() {
    try {
      while (requestsWaitedFor(topicRequests, topicProperty.getLinger()) || maxBatchSizeReached(topicRequests)) {
        createBatch(topicRequests).ifPresent(this::publishBatch);
      }
    } catch (final Exception ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  /**
   * Shuts down the consumer, waiting up to 60 seconds for both the scheduled and
   * worker executor services to terminate.
   */
  @Override
  @SneakyThrows
  public void shutdown() {
    await().thenRun(() -> {
      try {
        LOGGER.warn("Shutdown consumer {}", getClass().getSimpleName());

        scheduledExecutorService.shutdown();
        if (!scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
          LOGGER.warn("Scheduled executor service did not terminate in the specified time.");
          final List<Runnable> droppedTasks = scheduledExecutorService.shutdownNow();
          LOGGER.warn("Scheduled executor service was abruptly shut down. {} tasks will not be executed.", droppedTasks.size());
        }

        executorService.shutdown();
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
          LOGGER.warn("Executor service did not terminate in the specified time.");
          final List<Runnable> droppedTasks = executorService.shutdownNow();
          LOGGER.warn("Executor service was abruptly shut down. {} tasks will not be executed.", droppedTasks.size());
        }
      } catch (final InterruptedException ex) {
        LOGGER.error(ex.getMessage(), ex);
        Thread.currentThread().interrupt();
      }
    }).join();
  }

  /**
   * Checks whether the oldest request has waited longer than the batching window.
   *
   * @param requests          the request queue
   * @param batchingWindowInMs the batching window in milliseconds
   * @return true if the oldest request has exceeded the window
   */
  private boolean requestsWaitedFor(final BlockingQueue<RequestEntry<E>> requests, final long batchingWindowInMs) {
    return Optional.ofNullable(requests.peek()).map(oldestPendingRequest -> {
      final long oldestEntryWaitTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - oldestPendingRequest.getCreateTime());
      return oldestEntryWaitTime > batchingWindowInMs;
    }).orElse(false);
  }

  /**
   * Checks whether the queue has exceeded the maximum batch size.
   *
   * @param requests the request queue
   * @return true if the queue size exceeds the configured max batch size
   */
  private boolean maxBatchSizeReached(final BlockingQueue<RequestEntry<E>> requests) {
    return requests.size() > topicProperty.getMaxBatchSize();
  }

  /**
   * Checks whether a request can be added to the current batch based on size and count limits.
   *
   * @param batchSizeBytes     the current batch size in bytes
   * @param requestEntriesSize the current number of entries in the batch
   * @param request            the next request to consider adding
   * @return true if the request can be added
   */
  private boolean canAddToBatch(final int batchSizeBytes, final int requestEntriesSize, final RequestEntry<E> request) {
    return (batchSizeBytes < AbstractAmazonSnsConsumer.BATCH_SIZE_BYTES_THRESHOLD)
      && (requestEntriesSize < topicProperty.getMaxBatchSize())
      && Objects.nonNull(request);
  }

  /**
   * Checks whether the accumulated payload size is still within the 256 KB threshold.
   *
   * @param batchSizeBytes the current batch size in bytes
   * @return true if the batch is still within the size limit
   */
  private boolean canAddPayload(final int batchSizeBytes) {
    return batchSizeBytes <= AbstractAmazonSnsConsumer.BATCH_SIZE_BYTES_THRESHOLD;
  }

  /**
   * Drains requests from the queue and assembles them into a publish batch request.
   * Returns empty if no requests are available.
   *
   * @param requests the request queue
   * @return an optional containing the assembled batch request, or empty
   */
  @SneakyThrows
  private Optional<R> createBatch(final BlockingQueue<RequestEntry<E>> requests) {
    final AtomicInteger batchSizeBytes = new AtomicInteger(0);
    final List<RequestEntryInternal> requestEntries = new LinkedList<>();

    while (canAddToBatch(batchSizeBytes.get(), requestEntries.size(), requests.peek())) {
      final RequestEntry<E> request = requests.peek();

      final byte[] payload = requestEntryInternalFactory.convertPayload(request);

      final Integer messageBodySize = payload.length;
      final Integer messageAttributesSize = requestEntryInternalFactory.messageAttributesSize(request);

      final Integer messageSize = messageBodySize + messageAttributesSize;

      if (messageSize > BATCH_SIZE_BYTES_THRESHOLD) {
        final R publishBatchRequest = PublishRequestBuilder.<R, RequestEntryInternal>builder()
          .supplier(supplierPublishRequest())
          .entries(Collections.singletonList(requestEntryInternalFactory.create(request, payload)))
          .topicArn(topicProperty.getTopicArn())
          .build();

        final String stringPayload = new String(payload, StandardCharsets.UTF_8);

        final String message = String.format("The maximum allowed message size exceeding 256KB (262,144 bytes). Payload: %s, Headers: %s", stringPayload, request.getMessageHeaders());

        handleError(publishBatchRequest, new MaximumAllowedMessageException(message, requests.take()));
      }

      if (canAddPayload(batchSizeBytes.addAndGet(messageSize))) {
        requestEntries.add(requestEntryInternalFactory.create(requests.take(), payload));
      }
    }

    if (requestEntries.isEmpty()) {
      return Optional.empty();
    }

    LOGGER.debug("{}", requestEntries);

    return Optional.of(PublishRequestBuilder.<R, RequestEntryInternal>builder()
      .supplier(supplierPublishRequest())
      .entries(requestEntries)
      .topicArn(topicProperty.getTopicArn())
      .build());
  }

  /**
   * Returns a {@link CompletableFuture} that completes once all pending requests have
   * been processed (i.e., both the pending requests map and the topic requests queue are empty).
   *
   * @return a future that completes when all requests are drained
   */
  @Override
  @SneakyThrows
  public CompletableFuture<Void> await() {
    return CompletableFuture.runAsync(() -> {
      while (MapUtils.isNotEmpty(this.pendingRequests) || CollectionUtils.isNotEmpty(this.topicRequests)) {
        LockSupport.parkNanos(Duration.ofMillis(topicProperty.getLinger()).toNanos());
      }
    });
  }

}
// @formatter:on
