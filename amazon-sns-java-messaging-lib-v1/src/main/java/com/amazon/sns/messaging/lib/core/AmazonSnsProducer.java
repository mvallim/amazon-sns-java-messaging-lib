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

import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

// @formatter:off
/**
 * AWS SDK v1 implementation of {@link AbstractAmazonSnsProducer}. Delegates directly to the
 * abstract producer for request enqueuing and pending-request tracking.
 *
 * @param <E> the request entry payload type
 */
class AmazonSnsProducer<E> extends AbstractAmazonSnsProducer<E> {

  /**
   * Creates a new v1 producer.
   *
   * @param pendingRequests the shared map of pending requests keyed by request ID
   * @param topicRequests   the shared blocking queue for topic requests
   * @param executorService the executor service for async operations
   */
  public AmazonSnsProducer(
      final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests,
      final BlockingQueue<RequestEntry<E>> topicRequests) {
    super(pendingRequests, topicRequests);
  }

}
// @formatter:on
