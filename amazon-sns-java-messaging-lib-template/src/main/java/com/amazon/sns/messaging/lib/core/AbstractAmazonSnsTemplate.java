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

import java.util.concurrent.CompletableFuture;

import com.amazon.sns.messaging.lib.concurrent.AmazonSnsThreadPoolExecutor;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

// @formatter:off
/**
 * Abstract base template for Amazon SNS messaging. Provides the high-level API for
 * sending messages, shutting down, and awaiting completion. Delegates to a producer
 * and consumer for actual processing.
 *
 * @param <C> the Amazon SNS client type
 * @param <R> the publish batch request type
 * @param <O> the publish batch result type
 * @param <E> the request entry payload type
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractAmazonSnsTemplate<C, R, O, E> {

  private final AbstractAmazonSnsProducer<E> amazonSnsProducer;

  private final AbstractAmazonSnsConsumer<C, R, O, E> amazonSnsConsumer;

  /**
   * Sends a request entry to the SNS topic asynchronously.
   *
   * @param requestEntry the request entry containing the message payload and metadata
   * @return a {@link ListenableFuture} that completes with the success or failure result
   */
  public ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> send(final RequestEntry<E> requestEntry) {
    return amazonSnsProducer.send(requestEntry);
  }

  /**
   * Shuts down both the producer and consumer gracefully.
   */
  public void shutdown() {
    amazonSnsProducer.shutdown();
    amazonSnsConsumer.shutdown();
  }

  /**
   * Returns a future that completes once all pending requests are drained and processed.
   *
   * @return a {@link CompletableFuture} that completes when the consumer has finished
   */
  public CompletableFuture<Void> await() {
    return amazonSnsConsumer.await();
  }

  /**
   * Creates an {@link AmazonSnsThreadPoolExecutor} configured for the given topic property.
   * For FIFO topics, a single-threaded pool is used to guarantee order.
   *
   * @param topicProperty the topic configuration
   * @return a configured thread pool executor
   */
  protected static AmazonSnsThreadPoolExecutor getAmazonSnsThreadPoolExecutor(final TopicProperty topicProperty) {
    return topicProperty.isFifo() ? new AmazonSnsThreadPoolExecutor(1) : new AmazonSnsThreadPoolExecutor(topicProperty.getMaximumPoolSize());
  }

}
// @formatter:on
