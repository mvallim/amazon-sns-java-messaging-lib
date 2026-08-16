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

import com.amazon.sns.messaging.lib.metrics.AmazonSnsConsumerMetricsDecorator;
import com.amazon.sns.messaging.lib.model.TopicProperty;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

// @formatter:off
/**
 * AWS SDK v2 implementation of {@link AbstractAmazonSnsTemplate}. Provides the public API for
 * sending messages to Amazon SNS using the v2 {@link SnsClient}.
 *
 * @param <E> the request entry payload type
 */
public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<PublishBatchRequest, PublishBatchResponse, E> {

  private AmazonSnsTemplate(final Builder<SnsClient, PublishBatchRequest, PublishBatchResponse, E, AmazonSnsTemplate<E>> builder) {
    super(
      new AmazonSnsProducerImpl<>(
        builder.getPendingRequests(),
        builder.getTopicRequests()
      ),
      new AmazonSnsConsumerMetricsDecorator(
        new AmazonSnsConsumerImpl<>(
          builder.getAmazonSnsClient(),
          builder.getTopicProperty(),
          builder.getJsonMapper(),
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
   * @param amazonSnsClient  the v2 {@link SnsClient}
   * @param topicProperty    the topic configuration
   * @return a new builder instance
   */
  public static <E> Builder<SnsClient, PublishBatchRequest, PublishBatchResponse, E, AmazonSnsTemplate<E>> builder(
      final SnsClient amazonSnsClient,
      final TopicProperty topicProperty) {
    return new Builder<>(AmazonSnsTemplate::new, amazonSnsClient, topicProperty);
  }

}
// @formatter:on
