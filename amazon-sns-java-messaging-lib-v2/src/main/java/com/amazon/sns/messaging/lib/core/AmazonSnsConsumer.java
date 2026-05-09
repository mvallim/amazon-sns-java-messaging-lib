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
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

// @formatter:off
/**
 * AWS SDK v2 implementation of {@link AbstractAmazonSnsConsumer}. Uses the v2
 * {@link SnsClient} to publish {@link PublishBatchRequest} and handles
 * the resulting {@link PublishBatchResponse}.
 *
 * @param <E> the request entry payload type
 */
@SuppressWarnings("java:S6204")
class AmazonSnsConsumer<E> extends AbstractAmazonSnsConsumer<SnsClient, PublishBatchRequest, PublishBatchResponse, E> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AmazonSnsConsumer.class);

  private static final MessageAttributes messageAttributes = new MessageAttributes();

  /**
   * Creates a new v2 SNS consumer.
   *
   * @param amazonSnsClient  the v2 {@link SnsClient}
   * @param topicProperty    the topic configuration
   * @param objectMapper     the Jackson ObjectMapper for payload serialization
   * @param pendingRequests  the shared map of pending requests
   * @param topicRequests    the shared blocking queue of requests
   * @param executorService  the executor service for async publishing
   * @param publishDecorator an optional decorator for the publish request
   */
  public AmazonSnsConsumer(
      final SnsClient amazonSnsClient,
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
  protected PublishBatchResponse publish(final PublishBatchRequest publishBatchRequest) {
    return amazonSnsClient.publishBatch(publishBatchRequest);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected BiFunction<String, List<RequestEntryInternal>, PublishBatchRequest> supplierPublishRequest() {
    return (topicArn, requestEntries) -> {
      final List<PublishBatchRequestEntry> entries = requestEntries.stream()
        .map(entry -> PublishBatchRequestEntry.builder()
          .id(entry.getId())
          .subject(StringUtils.isNotBlank(entry.getSubject()) ? entry.getSubject() : null)
          .messageGroupId(StringUtils.isNotBlank(entry.getGroupId()) ? entry.getGroupId() : null)
          .messageDeduplicationId(StringUtils.isNotBlank(entry.getDeduplicationId()) ? entry.getDeduplicationId() : null)
          .messageAttributes(AmazonSnsConsumer.messageAttributes.messageAttributes(entry.getMessageHeaders()))
          .message(entry.getMessage())
          .build())
        .collect(Collectors.toList());
      return PublishBatchRequest.builder().publishBatchRequestEntries(entries).topicArn(topicArn).build();
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void handleError(final PublishBatchRequest publishBatchRequest, final Throwable throwable) {
    final String code = throwable instanceof AwsServiceException ? AwsServiceException.class.cast(throwable).awsErrorDetails().errorCode() : "000";
    final String message = throwable instanceof AwsServiceException ? AwsServiceException.class.cast(throwable).awsErrorDetails().errorMessage() : throwable.getMessage();

    AmazonSnsConsumer.LOGGER.error(throwable.getMessage(), throwable);

    publishBatchRequest.publishBatchRequestEntries().forEach(entry -> {
      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> listenableFuture = pendingRequests.remove(entry.id());
      listenableFuture.fail(ResponseFailEntry.builder()
        .withId(entry.id())
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
  protected void handleResponse(final PublishBatchResponse publishBatchResult) {
    publishBatchResult.successful().forEach(entry -> {
      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> listenableFuture = pendingRequests.remove(entry.id());
      listenableFuture.success(ResponseSuccessEntry.builder()
        .withId(entry.id())
        .withMessageId(entry.messageId())
        .withSequenceNumber(entry.sequenceNumber())
        .build());
    });

    publishBatchResult.failed().forEach(entry -> {
      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> listenableFuture = pendingRequests.remove(entry.id());
      listenableFuture.fail(ResponseFailEntry.builder()
        .withId(entry.id())
        .withCode(entry.code())
        .withMessage(entry.message())
        .withSenderFault(entry.senderFault())
        .build());
    });
  }

}
// @formatter:on
