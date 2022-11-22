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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sns.messaging.lib.model.PublishRequestBuilder;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;

// @formatter:off
@RequiredArgsConstructor
abstract class AbstractAmazonSnsProducer<R, O, E> extends Thread implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAmazonSnsProducer.class);

  protected final TopicProperty topicProperty;

  private final ObjectMapper objectMapper;

  protected final Map<String, ListenableFutureRegistry> pendingRequests;

  private final Queue<RequestEntry<E>> topicRequests;

  protected final ExecutorService executorService;

  private final ReentrantLock reentrantLock = new ReentrantLock();

  private final Condition empty = reentrantLock.newCondition();

  @Getter
  @Setter(value = AccessLevel.PRIVATE)
  private boolean running = true;

  protected abstract void publishBatch(final R publishBatchRequest);

  protected abstract void handleError(final R publishBatchRequest, final Exception ex);

  protected abstract void handleResponse(final O publishBatchResult);

  protected abstract BiFunction<String, List<RequestEntry<E>>, R> supplierPublishRequest();

  @Override
  @SneakyThrows
  public void run() {
    while (isRunning()) {
      try {
        reentrantLock.lock();

        while (CollectionUtils.isEmpty(topicRequests)) {
          empty.await();
        }

        final boolean maxWaitTimeElapsed = requestsWaitedFor(topicRequests, topicProperty.getLinger());
        final boolean maxBatchSizeReached = maxBatchSizeReached(topicRequests);

        if (maxWaitTimeElapsed || maxBatchSizeReached) {
          createBatch(topicRequests).ifPresent(this::publishBatch);
        }
      } catch (final Exception ex) {
        LOGGER.error(ex.getMessage(), ex);
        Thread.currentThread().interrupt();
      } finally {
        reentrantLock.unlock();
      }
    }
  }

  @SneakyThrows
  public void shutdown() {
    LOGGER.warn("Shutdown producer {}", getClass().getSimpleName());
    setRunning(false);
    executorService.shutdown();
    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
      LOGGER.warn("Executor did not terminate in the specified time.");
      final List<Runnable> droppedTasks = executorService.shutdownNow();
      LOGGER.warn("Executor was abruptly shut down. {} tasks will not be executed.", droppedTasks.size());
    }
  }

  private boolean requestsWaitedFor(final Queue<RequestEntry<E>> requests, final long batchingWindowInMs) {
    return Optional.ofNullable(requests.peek()).map(oldestPendingRequest -> {
      final long oldestEntryWaitTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - oldestPendingRequest.getCreateTime());
      return oldestEntryWaitTime > batchingWindowInMs;
    }).orElse(false);
  }

  private boolean maxBatchSizeReached(final Queue<RequestEntry<E>> requests) {
    return requests.size() > topicProperty.getMaxBatchSize();
  }

  private Optional<R> createBatch(final Queue<RequestEntry<E>> requests) {
    final List<RequestEntry<E>> requestEntries = new LinkedList<>();

    while (requestEntries.size() < topicProperty.getMaxBatchSize() && Objects.nonNull(requests.peek())) {
      final RequestEntry<E> requestEntry = requests.poll();
      requestEntries.add(requestEntry);
    }

    if (requestEntries.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(PublishRequestBuilder.<R, RequestEntry<E>>builder()
      .supplier(supplierPublishRequest())
      .entries(requestEntries)
      .topicArn(topicProperty.getTopicArn())
      .build());
  }

  public void wakeup() {
    try {
      reentrantLock.lock();

      if (CollectionUtils.isNotEmpty(topicRequests)) {
        empty.signal();
      }

    } finally {
      reentrantLock.unlock();
    }
  }

  public CompletableFuture<Void> await() {
    while (MapUtils.isNotEmpty(pendingRequests) || CollectionUtils.isNotEmpty(topicRequests)) {
      final List<?> futures = pendingRequests.entrySet().stream()
        .map(Entry::getValue)
        .map(ListenableFutureRegistry::completable)
        .collect(Collectors.toList());
      if (CollectionUtils.isNotEmpty(futures)) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
      }
    }
    return CompletableFuture.completedFuture(null);
  }

  @SneakyThrows
  protected String convertPayload(final E payload) {
    return payload instanceof String ? payload.toString() : objectMapper.writeValueAsString(payload);
  }

}
// @formatter:on
