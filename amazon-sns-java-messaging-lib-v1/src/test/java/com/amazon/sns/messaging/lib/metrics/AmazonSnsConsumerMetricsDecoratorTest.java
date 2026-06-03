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

package com.amazon.sns.messaging.lib.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazon.sns.messaging.lib.core.AmazonSnsConsumer;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sns.model.BatchResultErrorEntry;
import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchRequestEntry;
import com.amazonaws.services.sns.model.PublishBatchResult;
import com.amazonaws.services.sns.model.PublishBatchResultEntry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// @formatter:off
@ExtendWith(MockitoExtension.class)
class AmazonSnsConsumerMetricsDecoratorTest {

  private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:my-topic";

  @Mock
  private AmazonSnsConsumer<PublishBatchRequest, PublishBatchResult> delegate;

  @Mock
  private TopicProperty topicProperty;

  private MeterRegistry registry;

  private AmazonSnsConsumerMetricsDecorator sut;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    when(topicProperty.getTopicArn()).thenReturn(TOPIC_ARN);
    sut = new AmazonSnsConsumerMetricsDecorator(delegate, topicProperty, registry);
  }

  @Nested
  @DisplayName("publish()")
  class Publish {

    @Test
    @DisplayName("should delegate to the wrapped consumer")
    void shouldDelegateToWrappedConsumer() {
      final PublishBatchRequest request = batchRequest(2);
      when(delegate.publish(request)).thenReturn(successResult("id-1", "id-2"));

      sut.publish(request);

      verify(delegate, times(1)).publish(request);
    }

    @Test
    @DisplayName("should increment attempt counter on success")
    void shouldIncrementAttemptCounterOnSuccess() {
      when(delegate.publish(any())).thenReturn(successResult("id-1"));

      sut.publish(batchRequest(1));

      assertThat(attemptsCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should increment attempt counter even when delegate throws")
    void shouldIncrementAttemptCounterOnException() {
      when(delegate.publish(any())).thenThrow(new RuntimeException("connection refused"));

      assertThatThrownBy(() -> sut.publish(batchRequest(1))).isInstanceOf(RuntimeException.class);

      assertThat(attemptsCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should record batch size in distribution summary")
    void shouldRecordBatchSize() {
      when(delegate.publish(any())).thenReturn(successResult("a", "b", "c"));

      sut.publish(batchRequest(3));

      assertThat(batchSizeCount()).isEqualTo(1L);
      assertThat(batchSizeMean()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("should record duration in timer")
    void shouldRecordDurationInTimer() {
      when(delegate.publish(any())).thenReturn(successResult("x"));

      sut.publish(batchRequest(1));

      assertThat(timerCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should decrement inflight gauge after successful publish")
    void shouldDecrementInflightAfterSuccess() {
      when(delegate.publish(any())).thenReturn(successResult("y"));

      sut.publish(batchRequest(1));

      assertThat(inflightValue()).isZero();
    }

    @Test
    @DisplayName("should decrement inflight gauge even when delegate throws")
    void shouldDecrementInflightAfterException() {
      when(delegate.publish(any())).thenThrow(new RuntimeException("timeout"));

      assertThatThrownBy(() -> sut.publish(batchRequest(1))).isInstanceOf(RuntimeException.class);

      assertThat(inflightValue()).isZero();
    }

    @Test
    @DisplayName("should propagate RuntimeException from delegate unchanged")
    void shouldPropagateRuntimeException() {
      final RuntimeException cause = new RuntimeException("sns down");
      when(delegate.publish(any())).thenThrow(cause);

      assertThatThrownBy(() -> sut.publish(batchRequest(1))).isSameAs(cause);
    }

    @Test
    @DisplayName("should wrap checked Exception from delegate in RuntimeException")
    void shouldWrapCheckedExceptionInRuntimeException() {
      when(delegate.publish(any())).thenAnswer(inv -> {
        throw new Exception("checked");
      });

      assertThatThrownBy(() -> sut.publish(batchRequest(1))).isInstanceOf(RuntimeException.class).hasMessageContaining("checked");
    }
  }

  @Nested
  @DisplayName("handleResponse()")
  class HandleResponse {

    @Test
    @DisplayName("should delegate to the wrapped consumer")
    void shouldDelegateToWrappedConsumer() {
      final PublishBatchResult result = successResult("id-1");

      sut.handleResponse(result);

      verify(delegate, times(1)).handleResponse(result);
    }

    @Test
    @DisplayName("should increment success counter once per successful message")
    void shouldIncrementSuccessCounterPerMessage() {
      final PublishBatchResult result = successResult("id-1", "id-2", "id-3");

      sut.handleResponse(result);

      assertThat(successCount()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("should not increment success counter when there are no successful entries")
    void shouldNotIncrementSuccessCounterWhenEmpty() {
      final PublishBatchResult result = new PublishBatchResult();
      result.setSuccessful(Collections.emptyList());
      result.setFailed(Collections.singleton(failedEntry("id-1", "InvalidParameter")));

      sut.handleResponse(result);

      assertThat(successCount()).isZero();
    }

    @Test
    @DisplayName("should increment failure counter once per failed message")
    void shouldIncrementFailureCounterPerFailedMessage() {
      final PublishBatchResult result = new PublishBatchResult();
      result.setSuccessful(Collections.emptyList());
      result.setFailed(Arrays.asList(failedEntry("id-a", "InvalidParameter"), failedEntry("id-b", "MessageTooLong")));

      sut.handleResponse(result);

      assertThat(failureCountByCode("InvalidParameter")).isEqualTo(1.0);
      assertThat(failureCountByCode("MessageTooLong")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should accumulate failures with the same error code")
    void shouldAccumulateFailuresWithSameErrorCode() {
      final PublishBatchResult result = new PublishBatchResult();
      result.setSuccessful(Collections.emptyList());
      result.setFailed(Arrays.asList(failedEntry("id-a", "InvalidParameter"), failedEntry("id-b", "InvalidParameter"), failedEntry("id-c", "InvalidParameter")));

      sut.handleResponse(result);

      assertThat(failureCountByCode("InvalidParameter")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("should handle mixed batch with both successes and failures")
    void shouldHandleMixedBatch() {
      final PublishBatchResult result = new PublishBatchResult();
      result.setSuccessful(Arrays.asList(successEntry("id-ok-1"), successEntry("id-ok-2")));
      result.setFailed(Collections.singleton(failedEntry("id-bad", "KMSDisabled")));

      sut.handleResponse(result);

      assertThat(successCount()).isEqualTo(2.0);
      assertThat(failureCountByCode("KMSDisabled")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should tag failures with error_type 'amazon_service_exception'")
    void shouldTagFailuresWithAmazonErrorType() {
      final PublishBatchResult result = new PublishBatchResult();
      result.setSuccessful(Collections.emptyList());
      result.setFailed(Collections.singleton(failedEntry("id-x", "ThrottledException")));

      sut.handleResponse(result);

      final Counter failureCounter = registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
        .tag("error_code", "ThrottledException")
        .tag("error_type", AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_AMAZON)
        .counter();

      assertThat(failureCounter).isNotNull();
      assertThat(failureCounter.count()).isEqualTo(1.0);
    }
  }

  @Nested
  @DisplayName("handleError()")
  class HandleError {

    @Test
    @DisplayName("should delegate to the wrapped consumer")
    void shouldDelegateToWrappedConsumer() {
      final PublishBatchRequest request = batchRequest(1);
      final RuntimeException cause = new RuntimeException("transport error");

      sut.handleError(request, cause);

      verify(delegate, times(1)).handleError(request, cause);
    }

    @Test
    @DisplayName("should count all batch entries as failures on AmazonServiceException")
    void shouldCountAllEntriesAsFailures_onAmazonServiceException() {
      final PublishBatchRequest request = batchRequest(3);
      final AmazonServiceException cause = serviceException("InternalError");

      sut.handleError(request, cause);

      assertThat(failureCountByCode("InternalError")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("should tag AmazonServiceException failures with correct error_type")
    void shouldTagAmazonServiceExceptionWithCorrectErrorType() {
      final PublishBatchRequest request = batchRequest(1);
      final AmazonServiceException cause = serviceException("InternalError");

      sut.handleError(request, cause);

      final Counter counter = registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
        .tag("error_code", "InternalError")
        .tag("error_type", AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_AMAZON)
        .counter();

      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should use error code '000' for non-AmazonServiceException")
    void shouldUseDefaultErrorCode_forGenericException() {
      final PublishBatchRequest request = batchRequest(2);

      sut.handleError(request, new RuntimeException("network timeout"));

      assertThat(failureCountByCode("000")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should tag generic exceptions with error_type 'unknown'")
    void shouldTagGenericExceptionsWithUnknownErrorType() {
      final PublishBatchRequest request = batchRequest(1);

      sut.handleError(request, new RuntimeException("network timeout"));

      final Counter counter = registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
        .tag("error_code", "000")
        .tag("error_type", AbstractAmazonSnsConsumerMetricsDecorator.ERROR_TYPE_OTHER)
        .counter();

      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should count each entry individually when batch has multiple entries")
    void shouldCountEachEntryIndividually() {
      final PublishBatchRequest request = batchRequest(5);
      final AmazonServiceException cause = serviceException("ServiceUnavailable");

      sut.handleError(request, cause);

      assertThat(failureCountByCode("ServiceUnavailable")).isEqualTo(5.0);
    }
  }

  @Nested
  @DisplayName("lifecycle methods")
  class Lifecycle {

    @Test
    @DisplayName("shutdown() should delegate to the wrapped consumer")
    void shutdown_shouldDelegate() {
      sut.shutdown();
      verify(delegate, times(1)).shutdown();
    }

    @Test
    @DisplayName("await() should delegate to the wrapped consumer")
    void await_shouldDelegate() {
      sut.await();
      verify(delegate, times(1)).await();
    }
  }

  @Nested
  @DisplayName("null MeterRegistry")
  class NullRegistry {

    @Test
    @DisplayName("should not throw when MeterRegistry is null")
    void shouldNotThrowWhenRegistryIsNull() {
      final AmazonSnsConsumerMetricsDecorator nullRegistrySut = new AmazonSnsConsumerMetricsDecorator(delegate, topicProperty, null);

      when(delegate.publish(any())).thenReturn(successResult("id-1"));

      // should execute without NullPointerException
      nullRegistrySut.publish(batchRequest(1));
    }
  }

  private double attemptsCount() {
    return registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_ATTEMPTS)
      .tag("topic", TOPIC_ARN)
      .counter()
      .count();
  }

  private double successCount() {
    final Counter c = registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_SUCCESS)
      .tag("topic", TOPIC_ARN)
      .counter();

    return c == null ? 0.0 : c.count();
  }

  private double failureCountByCode(final String code) {
    final Counter c = registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_FAILURE)
      .tag("topic", TOPIC_ARN)
      .tag("error_code", code)
      .counter();

    return c == null ? 0.0 : c.count();
  }

  private long batchSizeCount() {
    return registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_BATCH_SIZE)
      .tag("topic", TOPIC_ARN)
      .summary()
      .count();
  }

  private double batchSizeMean() {
    return registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_BATCH_SIZE)
      .tag("topic", TOPIC_ARN)
      .summary()
      .mean();
  }

  private long timerCount() {
    return registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_DURATION)
      .tag("topic", TOPIC_ARN)
      .timer()
      .count();
  }

  private double inflightValue() {
    return registry.find(AbstractAmazonSnsConsumerMetricsDecorator.METRIC_PUBLISH_INFLIGHT)
      .tag("topic", TOPIC_ARN)
      .gauge()
      .value();
  }

  private static PublishBatchRequest batchRequest(final int count) {
    final PublishBatchRequest request = new PublishBatchRequest().withTopicArn(TOPIC_ARN);
    for (int i = 0; i < count; i++) {
      request.getPublishBatchRequestEntries().add(new PublishBatchRequestEntry().withId("id-" + i).withMessage("msg-" + i));
    }
    return request;
  }

  private static PublishBatchResult successResult(final String... ids) {
    final PublishBatchResult result = new PublishBatchResult();
    for (final String id : ids) {
      result.getSuccessful().add(successEntry(id));
    }
    result.setFailed(Collections.emptyList());
    return result;
  }

  private static PublishBatchResultEntry successEntry(final String id) {
    return new PublishBatchResultEntry().withId(id).withMessageId("msg-" + id);
  }

  private static BatchResultErrorEntry failedEntry(final String id, final String code) {
    return new BatchResultErrorEntry().withId(id).withCode(code).withMessage("error detail").withSenderFault(true);
  }

  private static AmazonServiceException serviceException(final String code) {
    final AmazonServiceException ex = new AmazonServiceException("Service error");
    ex.setErrorCode(code);
    return ex;
  }

}
// @formatter:on