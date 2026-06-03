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

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.amazon.sns.messaging.lib.concurrent.AmazonSnsThreadPoolExecutor;
import com.amazon.sns.messaging.lib.concurrent.RingBufferBlockingQueue;
import com.amazon.sns.messaging.lib.metrics.BlockingQueueMetricsDecorator;
import com.amazon.sns.messaging.lib.metrics.ExecutorServiceMetricsDecorator;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.AccessLevel;
import lombok.Getter;
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
  protected static ExecutorService getExecutorService(final TopicProperty topicProperty, final MeterRegistry meterRegistry) {
    return topicProperty.isFifo()
      ? new ExecutorServiceMetricsDecorator(
          new AmazonSnsThreadPoolExecutor(1),
          meterRegistry,
          topicProperty.getTopicArn()
        )
      : new ExecutorServiceMetricsDecorator(
          new AmazonSnsThreadPoolExecutor(topicProperty.getMaximumPoolSize()),
          meterRegistry,
          topicProperty.getTopicArn()
        );
  }

  @Getter
  public static final class Builder<C, R, O, E, T extends AbstractAmazonSnsTemplate<C, R, O, E>> {

    private final C amazonSnsClient;

    private final TopicProperty topicProperty;

    private ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests = new ConcurrentHashMap<>();

    private BlockingQueue<RequestEntry<E>> topicRequests;

    private ObjectMapper objectMapper = new ObjectMapper();

    private UnaryOperator<R> publishDecorator = UnaryOperator.identity();

    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final Function<Builder<C, R, O, E, T>, T> constructor;

    Builder(final Function<Builder<C, R, O, E, T>, T> constructor, final C amazonSnsClient, final TopicProperty topicProperty) {
      this.amazonSnsClient = Objects.requireNonNull(amazonSnsClient, "amazonSnsClient");
      this.topicProperty = Objects.requireNonNull(topicProperty, "topicProperty");
      this.constructor = Objects.requireNonNull(constructor, "constructor");
    }

    public Builder<C, R, O, E, T> pendingRequests(final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests) {
      this.pendingRequests = Objects.requireNonNull(pendingRequests, "pendingRequests");
      return this;
    }

    public Builder<C, R, O, E, T> topicRequests(final BlockingQueue<RequestEntry<E>> topicRequests) {
      this.topicRequests = Objects.requireNonNull(topicRequests, "topicRequests");
      return this;
    }

    public Builder<C, R, O, E, T> objectMapper(final ObjectMapper objectMapper) {
      this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
      return this;
    }

    public Builder<C, R, O, E, T> publishDecorator(final UnaryOperator<R> publishDecorator) {
      this.publishDecorator = Objects.requireNonNull(publishDecorator, "publishDecorator");
      return this;
    }

    public Builder<C, R, O, E, T> meterRegistry(final MeterRegistry meterRegistry) {
      this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
      return this;
    }

    public T build() {
      if (Objects.isNull(topicRequests)) {
        topicRequests = new RingBufferBlockingQueue<>(topicProperty.getMaximumPoolSize() * topicProperty.getMaxBatchSize());
      }

      topicRequests = new BlockingQueueMetricsDecorator<>(topicRequests, meterRegistry, topicProperty.getTopicArn());

      return constructor.apply(this);
    }

  }

}
// @formatter:on
