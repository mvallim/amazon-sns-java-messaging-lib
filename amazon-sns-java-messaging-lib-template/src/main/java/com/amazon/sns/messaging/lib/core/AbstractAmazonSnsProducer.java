/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.amazon.sns.messaging.lib.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

// @formatter:off
@RequiredArgsConstructor
abstract class AbstractAmazonSnsProducer<E> {

  private final ConcurrentMap<String, ListenableFutureRegistry> pendingRequests;

  private final BlockingQueue<RequestEntry<E>> topicRequests;

  private Integer maxTopicRequests = 0;

  private Integer maxPendingRequests = 0;

  @SneakyThrows
  public ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> send(final RequestEntry<E> requestEntry) {
    final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> trackPendingRequest = trackPendingRequest(requestEntry.getId());
    enqueueRequest(requestEntry);
    maxTopicRequests = Math.max(maxTopicRequests, topicRequests.size());
    maxPendingRequests = Math.max(maxPendingRequests, pendingRequests.size());
    System.out.println("topicRequests : " + maxTopicRequests);
    System.out.println("pendingRequests : " + maxPendingRequests);
    return trackPendingRequest;
  }

  @SneakyThrows
  private void enqueueRequest(final RequestEntry<E> requestEntry) {
    topicRequests.put(requestEntry);
  }

  private ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> trackPendingRequest(final String correlationId) {
    final ListenableFutureRegistry listenableFuture = new ListenableFutureRegistry();
    this.pendingRequests.put(correlationId, listenableFuture);
    return listenableFuture;
  }

}
// @formatter:on
