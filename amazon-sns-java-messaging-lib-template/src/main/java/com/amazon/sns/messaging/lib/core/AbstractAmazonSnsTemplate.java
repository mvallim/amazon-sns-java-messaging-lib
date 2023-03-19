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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

// @formatter:off
abstract class AbstractAmazonSnsTemplate<R, O, E> {

  protected final ConcurrentMap<String, ListenableFutureRegistry> pendingRequests = new ConcurrentHashMap<>();

  protected BlockingQueue<RequestEntry<E>> topicRequests;

  protected Semaphore semaphoreProducer;

  protected Semaphore semaphoreConsumer;

  protected AbstractAmazonSnsProducer<E> amazonSnsProducer;

  protected AbstractAmazonSnsConsumer<R, O, E> amazonSnsConsumer;

  public ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> send(final RequestEntry<E> requestEntry) {
    return this.amazonSnsProducer.send(requestEntry);
  }

  public void shutdown() {
    this.amazonSnsConsumer.shutdown();
  }

  public CompletableFuture<Void> await() {
    return this.amazonSnsConsumer.await();
  }

}
// @formatter:on
