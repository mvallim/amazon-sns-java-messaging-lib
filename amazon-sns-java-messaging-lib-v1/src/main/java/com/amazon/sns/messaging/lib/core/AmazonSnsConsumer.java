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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sns.messaging.lib.core.RequestEntryInternalFactory.RequestEntryInternal;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchRequestEntry;
import com.amazonaws.services.sns.model.PublishBatchResult;
import com.fasterxml.jackson.databind.ObjectMapper;

// @formatter:off
/**
 * AWS SDK v1 implementation of {@link AbstractAmazonSnsConsumer}. Uses the v1
 * {@link AmazonSNS} client to publish {@link PublishBatchRequest} and handles
 * the resulting {@link PublishBatchResult}.
 *
 * @param <E> the request entry payload type
 */
@SuppressWarnings("java:S6204")
class AmazonSnsConsumer<E> extends AbstractAmazonSnsConsumer<AmazonSNS, PublishBatchRequest, PublishBatchResult, E> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AmazonSnsConsumer.class);

  private static final MessageAttributes messageAttributes = new MessageAttributes();

  /**
   * Creates a new v1 SNS consumer.
   *
   * @param amazonSnsClient  the v1 {@link AmazonSNS} client
   * @param topicProperty    the topic configuration
   * @param objectMapper     the Jackson ObjectMapper for payload serialization
   * @param pendingRequests  the shared map of pending requests
   * @param topicRequests    the shared blocking queue of requests
   * @param executorService  the executor service for async publishing
   * @param publishDecorator an optional decorator for the publish request
   */
  public AmazonSnsConsumer(
    final AmazonSNS amazonSnsClient,
    final TopicProperty topicProperty,
    final ObjectMapper objectMapper,
    final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests,
    final BlockingQueue<RequestEntry<E>> topicRequests,
    final ExecutorService executorService,
    final UnaryOperator<PublishBatchRequest> publishDecorator) {
    super(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, executorService, publishDecorator);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected PublishBatchResult publish(final PublishBatchRequest publishBatchRequest) {
    return amazonSnsClient.publishBatch(publishBatchRequest);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected BiFunction<String, List<RequestEntryInternal>, PublishBatchRequest> supplierPublishRequest() {
    return (topicArn, requestEntries) -> {
      final List<PublishBatchRequestEntry> entries = requestEntries.stream()
        .map(entry -> new PublishBatchRequestEntry()
          .withId(entry.getId())
          .withSubject(StringUtils.isNotBlank(entry.getSubject()) ? entry.getSubject() : null)
          .withMessageGroupId(StringUtils.isNotBlank(entry.getGroupId()) ? entry.getGroupId() : null)
          .withMessageDeduplicationId(StringUtils.isNotBlank(entry.getDeduplicationId()) ? entry.getDeduplicationId() : null)
          .withMessageAttributes(AmazonSnsConsumer.messageAttributes.messageAttributes(entry.getMessageHeaders()))
          .withMessage(entry.getMessage()))
        .collect(Collectors.toList());
      return new PublishBatchRequest().withPublishBatchRequestEntries(entries).withTopicArn(topicArn);
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void handleError(final PublishBatchRequest publishBatchRequest, final Throwable throwable) {
    final String code = throwable instanceof AmazonServiceException ? AmazonServiceException.class.cast(throwable).getErrorCode() : "000";
    final String message = throwable instanceof AmazonServiceException ? AmazonServiceException.class.cast(throwable).getErrorMessage() : throwable.getMessage();

    AmazonSnsConsumer.LOGGER.error(throwable.getMessage(), throwable);

    publishBatchRequest.getPublishBatchRequestEntries().forEach(entry -> {
      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> listenableFuture = pendingRequests.remove(entry.getId());
      listenableFuture.fail(ResponseFailEntry.builder()
        .withId(entry.getId())
        .withCode(code)
        .withMessage(message)
        .withSenderFault(true)
        .build());
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void handleResponse(final PublishBatchResult publishBatchResult) {
    publishBatchResult.getSuccessful().forEach(entry -> {
      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> listenableFuture = pendingRequests.remove(entry.getId());
      listenableFuture.success(ResponseSuccessEntry.builder()
        .withId(entry.getId())
        .withMessageId(entry.getMessageId())
        .withSequenceNumber(entry.getSequenceNumber())
        .build());
    });

    publishBatchResult.getFailed().forEach(entry -> {
      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> listenableFuture = pendingRequests.remove(entry.getId());
      listenableFuture.fail(ResponseFailEntry.builder()
        .withId(entry.getId())
        .withCode(entry.getCode())
        .withMessage(entry.getMessage())
        .withSenderFault(entry.getSenderFault())
        .build());
    });
  }

}
// @formatter:on
