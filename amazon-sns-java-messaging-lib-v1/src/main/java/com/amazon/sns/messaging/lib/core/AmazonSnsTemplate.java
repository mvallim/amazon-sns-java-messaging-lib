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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

import com.amazon.sns.messaging.lib.concurrent.ExecutorsProvider;
import com.amazon.sns.messaging.lib.concurrent.RingBufferBlockingQueue;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchResult;
import com.fasterxml.jackson.databind.ObjectMapper;

// @formatter:off
/**
 * AWS SDK v1 implementation of {@link AbstractAmazonSnsTemplate}. Provides the public API for
 * sending messages to Amazon SNS using the v1 {@link AmazonSNS} client.
 *
 * @param <E> the request entry payload type
 */
public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<AmazonSNS, PublishBatchRequest, PublishBatchResult, E> {

  /**
   * Internal constructor that wires together the producer and consumer for the v1 SDK.
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param pendingRequests  the shared map of pending requests
   * @param topicRequests    the shared blocking queue of requests
   * @param objectMapper     the Jackson ObjectMapper for payload serialization
   * @param publishDecorator a decorator for the publish batch request
   */
  private AmazonSnsTemplate(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty,
      final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests,
      final BlockingQueue<RequestEntry<E>> topicRequests,
      final ObjectMapper objectMapper,
      final UnaryOperator<PublishBatchRequest> publishDecorator) {
    super(
      new AmazonSnsProducer<>(pendingRequests, topicRequests, ExecutorsProvider.getExecutorService()),
      new AmazonSnsConsumer<>(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, AbstractAmazonSnsTemplate.getAmazonSnsThreadPoolExecutor(topicProperty), publishDecorator)
    );
  }

  /**
   * Creates a new v1 SNS template with default settings.
   *
   * @param amazonSnsClient the v1 {@link AmazonSNS} client
   * @param topicProperty   the topic configuration
   */
  public AmazonSnsTemplate(final AmazonSNS amazonSnsClient, final TopicProperty topicProperty) {
    this(amazonSnsClient, topicProperty, UnaryOperator.identity());
  }

  /**
   * Creates a new v1 SNS template with a custom publish decorator.
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param publishDecorator a decorator for the publish batch request
   */
  public AmazonSnsTemplate(final AmazonSNS amazonSnsClient, final TopicProperty topicProperty, final UnaryOperator<PublishBatchRequest> publishDecorator) {
    this(amazonSnsClient, topicProperty, new ObjectMapper(), publishDecorator);
  }

  /**
   * Creates a new v1 SNS template with a custom topic request queue.
   *
   * @param amazonSnsClient the v1 {@link AmazonSNS} client
   * @param topicProperty   the topic configuration
   * @param topicRequests   the blocking queue for topic requests
   */
  public AmazonSnsTemplate(final AmazonSNS amazonSnsClient, final TopicProperty topicProperty, final BlockingQueue<RequestEntry<E>> topicRequests) {
    this(amazonSnsClient, topicProperty, topicRequests, UnaryOperator.identity());
  }

  /**
   * Creates a new v1 SNS template with a custom queue and publish decorator.
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param topicRequests    the blocking queue for topic requests
   * @param publishDecorator a decorator for the publish batch request
   */
  public AmazonSnsTemplate(final AmazonSNS amazonSnsClient, final TopicProperty topicProperty, final BlockingQueue<RequestEntry<E>> topicRequests, final UnaryOperator<PublishBatchRequest> publishDecorator) {
    this(amazonSnsClient, topicProperty, topicRequests, new ObjectMapper(), publishDecorator);
  }

  /**
   * Creates a new v1 SNS template with a custom ObjectMapper.
   *
   * @param amazonSnsClient the v1 {@link AmazonSNS} client
   * @param topicProperty   the topic configuration
   * @param objectMapper    the Jackson ObjectMapper for payload serialization
   */
  public AmazonSnsTemplate(final AmazonSNS amazonSnsClient, final TopicProperty topicProperty, final ObjectMapper objectMapper) {
    this(amazonSnsClient, topicProperty, objectMapper, UnaryOperator.identity());
  }

  /**
   * Creates a new v1 SNS template with a custom ObjectMapper and publish decorator.
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param objectMapper     the Jackson ObjectMapper for payload serialization
   * @param publishDecorator a decorator for the publish batch request
   */
  public AmazonSnsTemplate(final AmazonSNS amazonSnsClient, final TopicProperty topicProperty, final ObjectMapper objectMapper, final UnaryOperator<PublishBatchRequest> publishDecorator) {
    this(amazonSnsClient, topicProperty, new RingBufferBlockingQueue<>(topicProperty.getMaximumPoolSize() * topicProperty.getMaxBatchSize()), objectMapper, publishDecorator);
  }

  /**
   * Creates a new v1 SNS template with a custom queue and ObjectMapper.
   *
   * @param amazonSnsClient the v1 {@link AmazonSNS} client
   * @param topicProperty   the topic configuration
   * @param topicRequests   the blocking queue for topic requests
   * @param objectMapper    the Jackson ObjectMapper for payload serialization
   */
  public AmazonSnsTemplate(final AmazonSNS amazonSnsClient, final TopicProperty topicProperty, final BlockingQueue<RequestEntry<E>> topicRequests, final ObjectMapper objectMapper) {
    this(amazonSnsClient, topicProperty, topicRequests, objectMapper, UnaryOperator.identity());
  }

  /**
   * Creates a new v1 SNS template with full custom configuration.
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param topicRequests    the blocking queue for topic requests
   * @param objectMapper     the Jackson ObjectMapper for payload serialization
   * @param publishDecorator a decorator for the publish batch request
   */
  public AmazonSnsTemplate(final AmazonSNS amazonSnsClient, final TopicProperty topicProperty, final BlockingQueue<RequestEntry<E>> topicRequests, final ObjectMapper objectMapper, final UnaryOperator<PublishBatchRequest> publishDecorator) {
    this(amazonSnsClient, topicProperty, new ConcurrentHashMap<>(), topicRequests, objectMapper, publishDecorator);
  }

}
// @formatter:on
