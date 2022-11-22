
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

package com.amazon.sns.messaging.core;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.amazon.sns.messaging.model.RequestEntry;
import com.amazon.sns.messaging.model.ResponseFailEntry;
import com.amazon.sns.messaging.model.ResponseSuccessEntry;

// @formatter:off
abstract class AbstractAmazonSnsTemplate<R, O, E> {

  protected final Map<String, ListenableFutureRegistry> pendingRequests = new ConcurrentHashMap<>();

  protected final Queue<RequestEntry<E>> topicRequests = new LinkedBlockingQueue<>();

  protected AbstractAmazonSnsProducer<R, O, E> amazonSnsProducer;

  public ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> send(final RequestEntry<E> requestEntry) {
    try {
      return trackPendingRequest(enqueueRequest(requestEntry));
    } finally {
      amazonSnsProducer.wakeup();
    }
  }

  public void shutdown() {
    amazonSnsProducer.shutdown();
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
