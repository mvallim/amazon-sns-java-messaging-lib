package com.amazon.sns.messaging.core;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import com.amazon.sns.messaging.model.PublishRequestBuilder;
import com.amazon.sns.messaging.model.RequestEntry;
import com.amazon.sns.messaging.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

// @formatter:off
@RequiredArgsConstructor
abstract class AbstractAmazonSnsProducer<R, O, E> extends Thread implements Runnable {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  protected final TopicProperty topicProperty;

  private final BiFunction<String, List<RequestEntry<E>>, R> supplierPublishRequest;

  protected final Map<String, ListenableFutureRegistry> pendingRequests;

  private final Queue<RequestEntry<E>> topicRequests;

  private final ReentrantLock reentrantLock = new ReentrantLock();

  private final Condition empty = reentrantLock.newCondition();

  private boolean isRunning = true;

  protected abstract void publishBatch(final R publishBatchRequest);

  protected abstract void handleError(final R publishBatchRequest, final Exception ex);

  protected abstract void handleResponse(final O publishBatchResult);

  @Override
  @SneakyThrows
  public void run() {
    while (isRunning) {
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
        // Stub
      } finally {
        reentrantLock.unlock();
      }
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
      .supplier(supplierPublishRequest)
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
  protected static <E> String convertPayload(final E payload) {
    return payload instanceof String ? payload.toString() : objectMapper.writeValueAsString(payload);
  }

}
// @formatter:on
