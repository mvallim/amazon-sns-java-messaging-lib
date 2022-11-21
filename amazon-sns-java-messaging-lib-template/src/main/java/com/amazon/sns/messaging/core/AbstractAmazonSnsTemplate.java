package com.amazon.sns.messaging.core;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.amazon.sns.messaging.model.RequestEntry;
import com.amazon.sns.messaging.model.ResponseFailEntry;
import com.amazon.sns.messaging.model.ResponseSuccessEntry;

import lombok.RequiredArgsConstructor;

// @formatter:off
@RequiredArgsConstructor
abstract class AbstractAmazonSnsTemplate<R, O, E> {

  private final Map<String, ListenableFutureRegistry> pendingRequests = new ConcurrentHashMap<>();

  private final Queue<RequestEntry<E>> topicRequests = new LinkedBlockingQueue<>();

  private final AbstractAmazonSnsProducer<R, O, E> amazonSnsProducer;

  public ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> send(final RequestEntry<E> requestEntry) {
    try {
      return trackPendingRequest(enqueueRequest(requestEntry));
    } finally {
      amazonSnsProducer.wakeup();
    }
  }

  public CompletableFuture<Void> await() {
    return amazonSnsProducer.await();
  }

  private String enqueueRequest(final RequestEntry<E> requestEntry) {
    topicRequests.add(requestEntry);
    return requestEntry.getId();
  }

  private ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> trackPendingRequest(final String correlationId) {
    final ListenableFutureRegistry listenableFuture = new ListenableFutureRegistry();
    pendingRequests.put(correlationId, listenableFuture);
    return listenableFuture;
  }

}
// @formatter:on
