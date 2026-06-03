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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

// @formatter:off
/**
 * Abstract base producer for Amazon SNS. Enqueues request entries into a blocking queue
 * and tracks pending requests via a concurrent map. Actual consumption and batching is
 * handled by the corresponding consumer.
 *
 * @param <E> the request entry payload type
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractAmazonSnsProducer<E> {

  private final AtomicReference<State> state = new AtomicReference<>(State.RUNNIG);

  private final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests;

  private final BlockingQueue<RequestEntry<E>> topicRequests;

  /**
   * Sends a request entry by enqueuing it for batch processing.
   *
   * @param requestEntry the request to enqueue
   * @return a {@link ListenableFuture} that tracks the completion of this request
   */
  @SneakyThrows
  public ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> send(final RequestEntry<E> requestEntry) {
    if (State.RUNNIG.equals(state.get())) {
      return enqueueRequest(requestEntry);
    } else {
      final ListenableFutureImpl listenableFutureImpl = new ListenableFutureImpl();

      listenableFutureImpl.fail(ResponseFailEntry.builder()
        .withCode("000")
        .withId(requestEntry.getId())
        .withMessage(String.format("Producer is currently in %s mode; no further messages will be accepted.", state.get().name()))
        .withSenderFault(true)
        .build()
      );

      return listenableFutureImpl;
    }
  }

  public void shutdown() {
    state.compareAndSet(State.RUNNIG, State.SHUTDOWN);
  }

  /**
   * Creates a {@link ListenableFuture} for the request, registers it in the pending map,
   * and enqueues the request for batch processing.
   *
   * @param requestEntry the request to enqueue
   * @return a future that will complete when the request is processed
   */
  @SneakyThrows
  private ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> enqueueRequest(final RequestEntry<E> requestEntry) {
    final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> trackPendingRequest = new ListenableFutureImpl();
    pendingRequests.put(requestEntry.getId(), trackPendingRequest);
    topicRequests.put(requestEntry);
    return trackPendingRequest;
  }

  enum State {
    RUNNIG, SHUTDOWN
  }

}
// @formatter:on
