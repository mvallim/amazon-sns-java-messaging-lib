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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import lombok.SneakyThrows;

// @formatter:off
class AmazonSnsConsumer<E> extends AbstractAmazonSnsConsumer<PublishBatchRequest, PublishBatchResult, E> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AmazonSnsConsumer.class);

  private static final MessageAttributes messageAttributes = new MessageAttributes();

  private final AmazonSNS amazonSNS;

  public AmazonSnsConsumer(
    final AmazonSNS amazonSNS,
    final TopicProperty topicProperty,
    final ObjectMapper objectMapper,
    final ConcurrentMap<String, ListenableFutureRegistry> pendingRequests,
    final BlockingQueue<RequestEntry<E>> topicRequests) {
    super(topicProperty, objectMapper, pendingRequests, topicRequests, new AmazonSnsThreadPoolExecutor(topicProperty.getMaximumPoolSize()));
    this.amazonSNS = amazonSNS;
  }

  private void publish(final PublishBatchRequest publishBatchRequest) {
    try {
      handleResponse(this.amazonSNS.publishBatch(publishBatchRequest));
    } catch (final Exception ex) {
      handleError(publishBatchRequest, ex);
    }
  }

  @Override
  protected void publishBatch(final PublishBatchRequest publishBatchRequest) {
    if (this.topicProperty.isFifo()) {
      publish(publishBatchRequest);
    } else {
      CompletableFuture.runAsync(() -> publish(publishBatchRequest), this.executorService);
    }
  }

  @Override
  protected BiFunction<String, List<RequestEntry<E>>, PublishBatchRequest> supplierPublishRequest() {
    return (topicArn, requestEntries) -> {
      final List<PublishBatchRequestEntry> entries = requestEntries.stream()
        .map(entry -> new PublishBatchRequestEntry()
          .withId(entry.getId())
          .withSubject(StringUtils.isNotBlank(entry.getSubject()) ? entry.getSubject() : null)
          .withMessageGroupId(StringUtils.isNotBlank(entry.getGroupId()) ? entry.getGroupId() : null)
          .withMessageDeduplicationId(StringUtils.isNotBlank(entry.getDeduplicationId()) ? entry.getDeduplicationId() : null)
          .withMessageAttributes(messageAttributes.messageAttributes(entry.getMessageHeaders()))
          .withMessage(convertPayload(entry.getValue())))
        .collect(Collectors.toList());
      return new PublishBatchRequest().withPublishBatchRequestEntries(entries).withTopicArn(topicArn);
    };
  }

  @Override
  protected void handleError(final PublishBatchRequest publishBatchRequest, final Exception ex) {
    final String code = ex instanceof AmazonServiceException ? AmazonServiceException.class.cast(ex).getErrorCode() : "000";
    final String message = ex instanceof AmazonServiceException ? AmazonServiceException.class.cast(ex).getErrorMessage() : ex.getMessage();

    LOGGER.error(ex.getMessage(), ex);

    publishBatchRequest.getPublishBatchRequestEntries().forEach(entry -> {
      final ListenableFutureRegistry listenableFuture = this.pendingRequests.remove(entry.getId());
      listenableFuture.fail(ResponseFailEntry.builder()
        .withId(entry.getId())
        .withCode(code)
        .withMessage(message)
        .withSenderFault(true)
        .build());
    });
  }

  @Override
  @SneakyThrows
  protected void handleResponse(final PublishBatchResult publishBatchResult) {
    publishBatchResult.getSuccessful().forEach(entry -> {
      final ListenableFutureRegistry listenableFuture = pendingRequests.remove(entry.getId());
      listenableFuture.success(ResponseSuccessEntry.builder()
        .withId(entry.getId())
        .withMessageId(entry.getMessageId())
        .withSequenceNumber(entry.getSequenceNumber())
        .build());
    });

    publishBatchResult.getFailed().forEach(entry -> {
      final ListenableFutureRegistry listenableFuture = pendingRequests.remove(entry.getId());
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
