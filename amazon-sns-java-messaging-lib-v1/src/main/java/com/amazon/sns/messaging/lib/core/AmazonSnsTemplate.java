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

package com.amazon.sns.messaging.lib.core;

import java.util.concurrent.BlockingQueue;
import java.util.function.UnaryOperator;

import com.amazon.sns.messaging.lib.concurrent.RingBufferBlockingQueue;
import com.amazon.sns.messaging.lib.metrics.AmazonSnsConsumerMetricsDecorator;
import com.amazon.sns.messaging.lib.model.RequestEntry;
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
public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<PublishBatchRequest, PublishBatchResult, E> {

  private AmazonSnsTemplate(final Builder<AmazonSNS, PublishBatchRequest, PublishBatchResult, E, AmazonSnsTemplate<E>> builder) {
    super(
      new AmazonSnsProducerImpl<>(
        builder.getPendingRequests(),
        builder.getTopicRequests()
      ),
      new AmazonSnsConsumerMetricsDecorator(
        new AmazonSnsConsumerImpl<>(
          builder.getAmazonSnsClient(),
          builder.getTopicProperty(),
          builder.getObjectMapper(),
          builder.getPendingRequests(),
          builder.getTopicRequests(),
          getExecutorService(builder.getTopicProperty(), builder.getMeterRegistry()),
          builder.getPublishDecorator()
        ),
        builder.getTopicProperty(),
        builder.getMeterRegistry()
      )
    );
  }

  /**
   * Creates a new builder for constructing an {@link AmazonSnsTemplate}.
   *
   * @param <E>              the request entry payload type
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @return a new builder instance
   */
  public static <E> Builder<AmazonSNS, PublishBatchRequest, PublishBatchResult, E, AmazonSnsTemplate<E>> builder(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty) {
    return new Builder<>(AmazonSnsTemplate::new, amazonSnsClient, topicProperty);
  }

  /**
   * Creates a new v1 SNS template with default settings.
   *
   * @deprecated since 1.3.0, use {@link #builder(AmazonSNS, TopicProperty)} instead
   *
   * @param amazonSnsClient the v1 {@link AmazonSNS} client
   * @param topicProperty   the topic configuration
   */
  @Deprecated
  public AmazonSnsTemplate(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty) {
    this(amazonSnsClient, topicProperty, UnaryOperator.identity());
  }

  /**
   * Creates a new v1 SNS template with a custom publish decorator.
   *
   * @deprecated since 1.3.0, use {@link #builder(AmazonSNS, TopicProperty)} and {@link Builder#publishDecorator(UnaryOperator)} instead
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param publishDecorator a decorator for the publish batch request
   */
  @Deprecated
  public AmazonSnsTemplate(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty,
      final UnaryOperator<PublishBatchRequest> publishDecorator) {
    this(amazonSnsClient, topicProperty, new ObjectMapper(), publishDecorator);
  }

  /**
   * Creates a new v1 SNS template with a custom topic request queue.
   *
   * @deprecated since 1.3.0, use {@link #builder(AmazonSNS, TopicProperty)} and {@link Builder#topicRequests(BlockingQueue)} instead
   *
   * @param amazonSnsClient the v1 {@link AmazonSNS} client
   * @param topicProperty   the topic configuration
   * @param topicRequests   the blocking queue for topic requests
   */
  @Deprecated
  public AmazonSnsTemplate(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty,
      final BlockingQueue<RequestEntry<E>> topicRequests) {
    this(amazonSnsClient, topicProperty, topicRequests, UnaryOperator.identity());
  }

  /**
   * Creates a new v1 SNS template with a custom queue and publish decorator.
   *
   * @deprecated since 1.3.0, use {@link #builder(AmazonSNS, TopicProperty)} with {@link Builder#topicRequests(BlockingQueue)} and {@link Builder#publishDecorator(UnaryOperator)} instead
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param topicRequests    the blocking queue for topic requests
   * @param publishDecorator a decorator for the publish batch request
   */
  @Deprecated
  public AmazonSnsTemplate(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty,
      final BlockingQueue<RequestEntry<E>> topicRequests,
      final UnaryOperator<PublishBatchRequest> publishDecorator) {
    this(amazonSnsClient, topicProperty, topicRequests, new ObjectMapper(), publishDecorator);
  }

  /**
   * Creates a new v1 SNS template with a custom ObjectMapper.
   *
   * @deprecated since 1.3.0, use {@link #builder(AmazonSNS, TopicProperty)} and {@link Builder#objectMapper(ObjectMapper)} instead
   *
   * @param amazonSnsClient the v1 {@link AmazonSNS} client
   * @param topicProperty   the topic configuration
   * @param objectMapper    the Jackson ObjectMapper for payload serialization
   */
  @Deprecated
  public AmazonSnsTemplate(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty,
      final ObjectMapper objectMapper) {
    this(amazonSnsClient, topicProperty, objectMapper, UnaryOperator.identity());
  }

  /**
   * Creates a new v1 SNS template with a custom ObjectMapper and publish decorator.
   *
   * @deprecated since 1.3.0, use {@link #builder(AmazonSNS, TopicProperty)} with {@link Builder#objectMapper(ObjectMapper)} and {@link Builder#publishDecorator(UnaryOperator)} instead
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param objectMapper     the Jackson ObjectMapper for payload serialization
   * @param publishDecorator a decorator for the publish batch request
   */
  @Deprecated
  public AmazonSnsTemplate(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty,
      final ObjectMapper objectMapper,
      final UnaryOperator<PublishBatchRequest> publishDecorator) {
    this(amazonSnsClient, topicProperty, new RingBufferBlockingQueue<>(topicProperty.getMaximumPoolSize() * topicProperty.getMaxBatchSize()), objectMapper, publishDecorator);
  }

  /**
   * Creates a new v1 SNS template with a custom queue and ObjectMapper.
   *
   * @deprecated since 1.3.0, use {@link #builder(AmazonSNS, TopicProperty)} with {@link Builder#topicRequests(BlockingQueue)} and {@link Builder#objectMapper(ObjectMapper)} instead
   *
   * @param amazonSnsClient the v1 {@link AmazonSNS} client
   * @param topicProperty   the topic configuration
   * @param topicRequests   the blocking queue for topic requests
   * @param objectMapper    the Jackson ObjectMapper for payload serialization
   */
  @Deprecated
  public AmazonSnsTemplate(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty,
      final BlockingQueue<RequestEntry<E>> topicRequests,
      final ObjectMapper objectMapper) {
    this(amazonSnsClient, topicProperty, topicRequests, objectMapper, UnaryOperator.identity());
  }

  /**
   * Creates a new v1 SNS template with full custom configuration.
   *
   * @deprecated since 1.3.0, use {@link #builder(AmazonSNS, TopicProperty)} with builder setters instead
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param topicRequests    the blocking queue for topic requests
   * @param objectMapper     the Jackson ObjectMapper for payload serialization
   * @param publishDecorator a decorator for the publish batch request
   */
  @Deprecated
  public AmazonSnsTemplate(
      final AmazonSNS amazonSnsClient,
      final TopicProperty topicProperty,
      final BlockingQueue<RequestEntry<E>> topicRequests,
      final ObjectMapper objectMapper,
      final UnaryOperator<PublishBatchRequest> publishDecorator) {
    this(AmazonSnsTemplate.<E>builder(amazonSnsClient, topicProperty)
      .topicRequests(topicRequests)
      .objectMapper(objectMapper)
      .publishDecorator(publishDecorator)
    );
  }

}
// @formatter:on
