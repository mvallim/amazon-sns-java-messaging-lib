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

package com.amazon.sns.messaging.lib.metrics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazon.sns.messaging.lib.core.AmazonSnsConsumer;
import com.amazon.sns.messaging.lib.model.TopicProperty;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sns.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;
import software.amazon.awssdk.services.sns.model.PublishBatchResultEntry;

// @formatter:off
@ExtendWith(MockitoExtension.class)
class AmazonSnsConsumerMetricsDecoratorTest {

  private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:test-topic";

  @Spy
  private SimpleMeterRegistry meterRegistry;

  @Mock
  private AmazonSnsConsumer<PublishBatchRequest, PublishBatchResponse> delegate;

  @Mock
  private TopicProperty topicProperty;

  private AmazonSnsConsumerMetricsDecorator decorator;

  @BeforeEach
  void setUp() {
    when(topicProperty.getTopicArn()).thenReturn(TOPIC_ARN);

    decorator = new AmazonSnsConsumerMetricsDecorator(delegate, topicProperty, meterRegistry);
  }

  @Test
  void testConstructorRegistersPublishAttemptsCounter() {
    final Counter counter = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_ATTEMPTS)
      .tag("topic", TOPIC_ARN)
      .counter();

    assertThat(counter, notNullValue());
  }

  @Test
  void testConstructorRegistersPublishSuccessCounter() {
    final Counter counter = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_SUCCESS)
      .tag("topic", TOPIC_ARN)
      .counter();

    assertThat(counter, notNullValue());
  }

  @Test
  void testConstructorRegistersPublishDurationTimer() {
    final Timer timer = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_DURATION)
      .tag("topic", TOPIC_ARN)
      .timer();

    assertThat(timer, notNullValue());
  }

  @Test
  void testConstructorRegistersPublishBatchSizeSummary() {
    final DistributionSummary summary = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_BATCH_SIZE)
      .tag("topic", TOPIC_ARN)
      .summary();

    assertThat(summary, notNullValue());
  }

  @Test
  void testConstructorRegistersInflightGauge() {
    final Gauge gauge = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_INFLIGHT)
      .tag("topic", TOPIC_ARN)
      .gauge();

    assertThat(gauge, notNullValue());
  }

  @Test
  void testConstructorWithNullMeterRegistryDoesNotThrow() {
    final AmazonSnsConsumerMetricsDecorator nullRegistryDecorator = new AmazonSnsConsumerMetricsDecorator(delegate, topicProperty, null);

    assertThat(nullRegistryDecorator, notNullValue());
  }

  @Test
  void testPublishIncrementsAttemptsCounter() {
    final PublishBatchRequest request = buildRequest(3);
    when(delegate.publish(request)).thenReturn(PublishBatchResponse.builder().build());

    decorator.publish(request);

    final double count = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_ATTEMPTS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();

    assertThat(count, equalTo(1.0));
  }

  @Test
  void testPublishRecordsBatchSize() {
    final PublishBatchRequest request = buildRequest(5);
    when(delegate.publish(request)).thenReturn(PublishBatchResponse.builder().build());

    decorator.publish(request);

    final double mean = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_BATCH_SIZE)
      .tag("topic", TOPIC_ARN)
      .summary()
      .mean();

    assertThat(mean, equalTo(5.0));
  }

  @Test
  void testPublishRecordsTimer() {
    final PublishBatchRequest request = buildRequest(2);
    when(delegate.publish(request)).thenReturn(PublishBatchResponse.builder().build());

    decorator.publish(request);

    final long timerCount = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_DURATION)
      .tag("topic", TOPIC_ARN)
      .timer()
      .count();

    assertThat(timerCount, equalTo(1L));
  }

  @Test
  void testPublishInflightGaugeIsZeroAfterCompletion() {
    final PublishBatchRequest request = buildRequest(1);
    when(delegate.publish(request)).thenReturn(PublishBatchResponse.builder().build());

    decorator.publish(request);

    final double inflight = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_INFLIGHT)
      .tag("topic", TOPIC_ARN)
      .gauge()
      .value();

    assertThat(inflight, equalTo(0.0));
  }

  @Test
  void testPublishInflightGaugeIsZeroAfterException() {
    final PublishBatchRequest request = buildRequest(1);
    when(delegate.publish(request)).thenThrow(new RuntimeException("delegate error"));

    assertThrows(RuntimeException.class, () -> decorator.publish(request));

    final double inflight = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_INFLIGHT)
      .tag("topic", TOPIC_ARN)
      .gauge()
      .value();

    assertThat(inflight, equalTo(0.0));
  }

  @Test
  void testPublishDelegatesCallToDelegate() {
    final PublishBatchRequest request = buildRequest(2);
    final PublishBatchResponse expectedResult = PublishBatchResponse.builder().build();
    when(delegate.publish(request)).thenReturn(expectedResult);

    final PublishBatchResponse result = decorator.publish(request);

    assertThat(result, equalTo(expectedResult));
    verify(delegate, times(1)).publish(request);
  }

  @Test
  void testPublishRethrowsRuntimeException() {
    final PublishBatchRequest request = buildRequest(1);
    when(delegate.publish(request)).thenThrow(new IllegalStateException("boom"));

    final RuntimeException thrown = assertThrows(RuntimeException.class, () -> decorator.publish(request));

    assertThat(thrown, instanceOf(IllegalStateException.class));
  }

  @Test
  void testPublishMultipleTimesAccumulatesAttemptsCounter() {
    final PublishBatchRequest request = buildRequest(1);
    when(delegate.publish(request)).thenReturn(PublishBatchResponse.builder().build());

    decorator.publish(request);
    decorator.publish(request);
    decorator.publish(request);

    final double count = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_ATTEMPTS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();

    assertThat(count, equalTo(3.0));
  }

  @Test
  void testHandleResponseIncrementsSuccessCounter() {
    final PublishBatchResponse result = buildResult(3, 0);

    decorator.handleResponse(result);

    final double count = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_SUCCESS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();

    assertThat(count, equalTo(3.0));
  }

  @Test
  void testHandleResponseDoesNotIncrementSuccessCounterWhenZeroSuccessful() {
    final PublishBatchResponse result = buildResult(0, 2);

    decorator.handleResponse(result);

    final double count = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_SUCCESS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();

    assertThat(count, equalTo(0.0));
  }

  @Test
  void testHandleResponseIncrementsFailureCounterForEachFailedEntry() throws IllegalAccessException {
    final PublishBatchResponse result = buildResult(0, 2);
    FieldUtils.writeDeclaredField(result.failed().get(0), "code", "InvalidParameter", true);
    FieldUtils.writeDeclaredField(result.failed().get(1), "code", "AuthorizationError", true);

    decorator.handleResponse(result);

    final double count1 = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_CODE, "InvalidParameter")
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_TYPE, AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_AMAZON)
      .counter()
      .count();

    final double count2 = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_CODE, "AuthorizationError")
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_TYPE, AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_AMAZON)
      .counter()
      .count();

    assertThat(count1, equalTo(1.0));
    assertThat(count2, equalTo(1.0));
  }

  @Test
  void testHandleResponseDelegatesCallToDelegate() {
    final PublishBatchResponse result = buildResult(1, 0);

    decorator.handleResponse(result);

    verify(delegate, times(1)).handleResponse(result);
  }

  @Test
  void testHandleResponseWithMixedSuccessAndFailure() throws IllegalAccessException {
    final PublishBatchResponse result = buildResult(2, 1);
    FieldUtils.writeDeclaredField(result.failed().get(0), "code", "Throttling", true);

    decorator.handleResponse(result);

    final double successCount = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_SUCCESS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();

    final double failureCount = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_CODE, "Throttling")
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_TYPE, AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_AMAZON)
      .counter()
      .count();

    assertThat(successCount, equalTo(2.0));
    assertThat(failureCount, equalTo(1.0));
  }

  @Test
  void testHandleResponseAccumulatesSuccessCounterAcrossMultipleCalls() {
    decorator.handleResponse(buildResult(2, 0));
    decorator.handleResponse(buildResult(3, 0));

    final double count = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_SUCCESS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();

    assertThat(count, equalTo(5.0));
  }

  @Test
  void testHandleErrorWithAwsServiceExceptionIncrementsFailureCounter() {
    final PublishBatchRequest request = buildRequest(3);
    final AwsServiceException ex = AwsServiceException.builder()
        .awsErrorDetails(AwsErrorDetails.builder().errorCode("ServiceUnavailable").build())
        .message("Service error")
        .build();

    decorator.handleError(request, ex);

    final double count = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_CODE, "ServiceUnavailable")
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_TYPE, AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_AMAZON)
      .counter()
      .count();

    assertThat(count, equalTo(3.0));
  }

  @Test
  void testHandleErrorWithUnknownExceptionIncrementsFailureCounterWithCode000() {
    final PublishBatchRequest request = buildRequest(2);
    final RuntimeException ex = new RuntimeException("unexpected");

    decorator.handleError(request, ex);

    final double count = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_CODE, "000")
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_TYPE, AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_OTHER)
      .counter()
      .count();

    assertThat(count, equalTo(2.0));
  }

  @Test
  void testHandleErrorWithAwsServiceExceptionUsesAmazonErrorType() {
    final PublishBatchRequest request = buildRequest(1);

    final AwsServiceException ex = AwsServiceException.builder()
      .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").build())
      .message("error")
      .build();

    decorator.handleError(request, ex);

    final Counter counter = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_TYPE, AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_AMAZON)
      .counter();

    assertThat(counter, notNullValue());
    assertThat(counter.count(), equalTo(1.0));
  }

  @Test
  void testHandleErrorWithNonAmazonExceptionUsesUnknownErrorType() {
    final PublishBatchRequest request = buildRequest(1);
    final NullPointerException ex = new NullPointerException("npe");

    decorator.handleError(request, ex);

    final Counter counter = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_TYPE, AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_OTHER)
      .counter();

    assertThat(counter, notNullValue());
    assertThat(counter.count(), equalTo(1.0));
  }

  @Test
  void testHandleErrorDelegatesCallToDelegate() {
    final PublishBatchRequest request = buildRequest(1);
    final RuntimeException ex = new RuntimeException("err");

    decorator.handleError(request, ex);

    verify(delegate, times(1)).handleError(request, ex);
  }

  @Test
  void testHandleErrorCountsAllEntriesInBatch() {
    final PublishBatchRequest request = buildRequest(10);
    final RuntimeException ex = new RuntimeException("bulk failure");

    decorator.handleError(request, ex);

    final double count = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_CODE, "000")
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_TYPE, AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_OTHER)
      .counter()
      .count();

    assertThat(count, equalTo(10.0));
  }

  @Test
  void testShutdownDelegatesCallToDelegate() {
    decorator.shutdown();
    verify(delegate, times(1)).shutdown();
  }

  @Test
  void testAwaitDelegatesCallToDelegate() {
    when(delegate.await()).thenReturn(CompletableFuture.completedFuture(null));

    decorator.await();

    verify(delegate, times(1)).await();
  }

  @Test
  void testAwaitReturnsTheDelegatesFuture() {
    final CompletableFuture<Void> expected = CompletableFuture.completedFuture(null);
    when(delegate.await()).thenReturn(expected);

    final CompletableFuture<Void> result = decorator.await();

    assertThat(result, equalTo(expected));
  }

  @Test
  void testFullSuccessfulPublishFlowUpdatesAllMetrics() {
    final PublishBatchRequest request = buildRequest(4);
    final PublishBatchResponse batchResult = buildResult(4, 0);
    when(delegate.publish(request)).thenReturn(batchResult);

    final PublishBatchResponse result = decorator.publish(request);
    decorator.handleResponse(result);

    final double attempts = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_ATTEMPTS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();

    final double success = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_SUCCESS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();

    final double batchMean = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_BATCH_SIZE)
      .tag("topic", TOPIC_ARN)
      .summary()
      .mean();

    final long timerCount = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_DURATION)
      .tag("topic", TOPIC_ARN)
      .timer()
      .count();

    assertThat(attempts, equalTo(1.0));
    assertThat(success, equalTo(4.0));
    assertThat(batchMean, equalTo(4.0));
    assertThat(timerCount, equalTo(1L));
  }

  @Test
  void testFullErrorFlowUpdatesFailureMetrics() {
    final PublishBatchRequest request = buildRequest(3);
    final AwsServiceException ex = AwsServiceException.builder()
      .awsErrorDetails(AwsErrorDetails.builder().errorCode("RequestExpired").build())
      .message("timeout")
      .build();

    when(delegate.publish(request)).thenThrow(ex);

    assertThrows(RuntimeException.class, () -> decorator.publish(request));
    decorator.handleError(request, ex);

    final double attempts = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_ATTEMPTS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();

    final double failures = meterRegistry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_CODE, "RequestExpired")
      .tag(AbstractAmazonSnsConsumerMetricsDecorator.TAG_ERROR_TYPE, AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_AMAZON)
      .counter().count();

    assertThat(attempts, equalTo(1.0));
    assertThat(failures, equalTo(3.0));
  }

  private PublishBatchRequest buildRequest(final int size) {
    final List<PublishBatchRequestEntry> entries = new ArrayList<>();

    IntStream.range(0, size).forEach(i -> {
      entries.add(PublishBatchRequestEntry.builder().id("id-" + i).message("msg-" + i).build());
    });

    return PublishBatchRequest.builder().publishBatchRequestEntries(entries).build();
  }

  private PublishBatchResponse buildResult(final int successCount, final int failureCount) {
    final List<PublishBatchResultEntry> successful = new ArrayList<>();

    IntStream.range(0, successCount).forEach(i -> {
      successful.add(PublishBatchResultEntry.builder().id("id-" + i).messageId("msg-id-" + i).build());
    });

    final List<BatchResultErrorEntry> failed = new java.util.ArrayList<>();

    IntStream.range(0, failureCount).forEach(i -> {
      failed.add(BatchResultErrorEntry.builder().id("id-fail-" + i).code("ErrorCode").message("error").build());
    });

    return PublishBatchResponse.builder().successful(successful).failed(failed).build();
  }

}