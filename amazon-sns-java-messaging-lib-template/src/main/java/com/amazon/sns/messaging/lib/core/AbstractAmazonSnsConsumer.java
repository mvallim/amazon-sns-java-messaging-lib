/*
 * Copyright 2023 the original author or authors.
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
import java.util.function.BiFunction;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sns.messaging.lib.model.PublishRequestBuilder;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;

// @formatter:off
//@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractAmazonSnsConsumer<R, O, E> implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAmazonSnsConsumer.class);

  private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

  protected final TopicProperty topicProperty;

  private final ObjectMapper objectMapper;

  protected final ConcurrentMap<String, ListenableFutureRegistry> pendingRequests;

  private final BlockingQueue<RequestEntry<E>> topicRequests;

  protected final ExecutorService executorService;

  protected abstract void publishBatch(final R publishBatchRequest);

  protected abstract void handleError(final R publishBatchRequest, final Throwable throwable);

  protected abstract void handleResponse(final O publishBatchResult);

  protected abstract BiFunction<String, List<RequestEntry<E>>, R> supplierPublishRequest();

  protected AbstractAmazonSnsConsumer(
      final TopicProperty topicProperty,
      final ObjectMapper objectMapper,
      final ConcurrentMap<String, ListenableFutureRegistry> pendingRequests,
      final BlockingQueue<RequestEntry<E>> topicRequests,
      final ExecutorService executorService) {

    this.topicProperty = topicProperty;
    this.objectMapper = objectMapper;
    this.pendingRequests = pendingRequests;
    this.topicRequests = topicRequests;
    this.executorService = executorService;

    scheduledExecutorService.scheduleAtFixedRate(this, 0, topicProperty.getLinger(), TimeUnit.MILLISECONDS);
  }

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

  @SneakyThrows
  public void shutdown() {
    LOGGER.warn("Shutdown consumer {}", getClass().getSimpleName());

    scheduledExecutorService.shutdown();
    if (!scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
      LOGGER.warn("Scheduled executor service did not terminate in the specified time.");
      final List<Runnable> droppedTasks = executorService.shutdownNow();
      LOGGER.warn("Scheduled executor service was abruptly shut down. {} tasks will not be executed.", droppedTasks.size());
    }

    executorService.shutdown();
    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
      LOGGER.warn("Executor service did not terminate in the specified time.");
      final List<Runnable> droppedTasks = executorService.shutdownNow();
      LOGGER.warn("Executor service was abruptly shut down. {} tasks will not be executed.", droppedTasks.size());
    }
  }

  private boolean requestsWaitedFor(final BlockingQueue<RequestEntry<E>> requests, final long batchingWindowInMs) {
    return Optional.ofNullable(requests.peek()).map(oldestPendingRequest -> {
      final long oldestEntryWaitTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - oldestPendingRequest.getCreateTime());
      return oldestEntryWaitTime > batchingWindowInMs;
    }).orElse(false);
  }

  private boolean maxBatchSizeReached(final BlockingQueue<RequestEntry<E>> requests) {
    return requests.size() > topicProperty.getMaxBatchSize();
  }

  @SneakyThrows
  private Optional<R> createBatch(final BlockingQueue<RequestEntry<E>> requests) {
    final List<RequestEntry<E>> requestEntries = new LinkedList<>();

    while (requestEntries.size() < topicProperty.getMaxBatchSize() && Objects.nonNull(requests.peek())) {
      final RequestEntry<E> requestEntry = requests.take();
      requestEntries.add(requestEntry);
    }

    if (requestEntries.isEmpty()) {
      return Optional.empty();
    }

    LOGGER.debug("{}", requestEntries);

    return Optional.of(PublishRequestBuilder.<R, RequestEntry<E>>builder()
      .supplier(supplierPublishRequest())
      .entries(requestEntries)
      .topicArn(topicProperty.getTopicArn())
      .build());
  }

  @SneakyThrows
  public CompletableFuture<Void> await() {
    return CompletableFuture.runAsync(() -> {
      while (
        MapUtils.isNotEmpty(this.pendingRequests) ||
        CollectionUtils.isNotEmpty(this.topicRequests)) {
        sleep(topicProperty.getLinger());
      }
    });
  }

  @SneakyThrows
  protected String convertPayload(final E payload) {
    return payload instanceof String ? payload.toString() : objectMapper.writeValueAsString(payload);
  }

  @SneakyThrows
  private static void sleep(final long millis) {
    Thread.sleep(millis);
  }

}
// @formatter:on
