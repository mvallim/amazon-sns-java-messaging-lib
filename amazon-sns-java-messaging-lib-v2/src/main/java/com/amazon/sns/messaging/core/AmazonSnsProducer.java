package com.amazon.sns.messaging.core;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.amazon.sns.messaging.model.RequestEntry;
import com.amazon.sns.messaging.model.ResponseFailEntry;
import com.amazon.sns.messaging.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

// @formatter:off
class AmazonSnsProducer<E> extends AbstractAmazonSnsProducer<PublishBatchRequest, PublishBatchResponse, E> {

  private final ExecutorService executorService;

  private static final MessageAttributes messageAttributes = new MessageAttributes();

  private final SnsClient amazonSNS;

  public AmazonSnsProducer(final SnsClient amazonSNS, final TopicProperty topicProperty, final ObjectMapper objectMapper, final Map<String, ListenableFutureRegistry> pendingRequests, final Queue<RequestEntry<E>> topicRequests) {
    super(topicProperty, objectMapper, pendingRequests, topicRequests);
    this.executorService = new ThreadPoolExecutor(2, topicProperty.getMaximumPoolSize(), 60, TimeUnit.SECONDS, new SynchronousQueue<>(), new BlockingSubmissionPolicy(30000));
    this.amazonSNS = amazonSNS;
  }

  private void publish(final PublishBatchRequest publishBatchRequest) {
    try {
      handleResponse(amazonSNS.publishBatch(publishBatchRequest));
    } catch (final Exception ex) {
      handleError(publishBatchRequest, ex);
    }
  }

  @Override
  public void shutdown() {
    super.shutdown();
    executorService.shutdown();
  }

  @Override
  protected void publishBatch(final PublishBatchRequest publishBatchRequest) {
    if (topicProperty.isFifo()) {
      publish(publishBatchRequest);
    } else {
      CompletableFuture.runAsync(() -> publish(publishBatchRequest), executorService);
    }
  }

  @Override
  protected BiFunction<String, List<RequestEntry<E>>, PublishBatchRequest> supplierPublishRequest() {
    return (topicArn, requestEntries) -> {
      final List<PublishBatchRequestEntry> entries = requestEntries.stream()
        .map(entry -> PublishBatchRequestEntry.builder()
          .id(entry.getId())
          .subject(StringUtils.isNotBlank(entry.getSubject()) ? entry.getSubject() : null)
          .messageGroupId(StringUtils.isNotBlank(entry.getGroupId()) ? entry.getGroupId() : null)
          .messageDeduplicationId(StringUtils.isNotBlank(entry.getDeduplicationId()) ? entry.getDeduplicationId() : null)
          .messageAttributes(messageAttributes.messageAttributes(entry.getMessageHeaders()))
          .message(convertPayload(entry.getValue()))
          .build())
        .collect(Collectors.toList());
      return PublishBatchRequest.builder().publishBatchRequestEntries(entries).topicArn(topicArn).build();
    };
  }

  @Override
  protected void handleError(final PublishBatchRequest publishBatchRequest, final Exception ex) {
    final String code = ex instanceof AwsServiceException ? AwsServiceException.class.cast(ex).awsErrorDetails().errorCode() : "000";
    final String message = ex instanceof AwsServiceException ? AwsServiceException.class.cast(ex).awsErrorDetails().errorMessage() : ex.getMessage();

    publishBatchRequest.publishBatchRequestEntries().forEach(entry -> {
      final ListenableFutureRegistry listenableFuture = pendingRequests.remove(entry.id());
      listenableFuture.fail(ResponseFailEntry.builder()
        .withId(entry.id())
        .withCode(code)
        .withMessage(message)
        .withSenderFault(true)
        .build());
    });
  }

  @Override
  protected void handleResponse(final PublishBatchResponse publishBatchResult) {
    publishBatchResult.successful().forEach(entry -> {
      final ListenableFutureRegistry listenableFuture = pendingRequests.remove(entry.id());
      listenableFuture.success(ResponseSuccessEntry.builder()
        .withId(entry.id())
        .withMessageId(entry.messageId())
        .withSequenceNumber(entry.sequenceNumber())
        .build());
    });

    publishBatchResult.failed().forEach(entry -> {
      final ListenableFutureRegistry listenableFuture = pendingRequests.remove(entry.id());
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
