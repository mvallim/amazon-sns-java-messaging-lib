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
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

// @formatter:off
public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<SnsClient, PublishBatchRequest, PublishBatchResponse, E> {

  public AmazonSnsTemplate(
      final SnsClient amazonSnsClient,
      final TopicProperty topicProperty,
      final ConcurrentMap<String, ListenableFutureRegistry> pendingRequests,
      final BlockingQueue<RequestEntry<E>> topicRequests,
      final ObjectMapper objectMapper) {
    super(
      new AmazonSnsProducer<>(pendingRequests, topicRequests, Executors.newSingleThreadExecutor()),
      new AmazonSnsConsumer<>(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, getAmazonSnsThreadPoolExecutor(topicProperty))
    );
  }

  public AmazonSnsTemplate(final SnsClient amazonSNS, final TopicProperty topicProperty, final BlockingQueue<RequestEntry<E>> topicRequests) {
    this(amazonSNS, topicProperty, new ConcurrentHashMap<>(), topicRequests, new ObjectMapper());
  }

  public AmazonSnsTemplate(final SnsClient amazonSNS, final TopicProperty topicProperty, final ObjectMapper objectMapper) {
    this(amazonSNS, topicProperty, new ConcurrentHashMap<>(), new LinkedBlockingQueue<>(topicProperty.getMaximumPoolSize() * topicProperty.getMaxBatchSize()), objectMapper);
  }

  public AmazonSnsTemplate(final SnsClient amazonSNS, final TopicProperty topicProperty) {
    this(amazonSNS, topicProperty, new ObjectMapper());
  }

}
// @formatter:on
