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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sns.messaging.lib.core.AmazonSnsConsumer;
import com.amazon.sns.messaging.lib.model.TopicProperty;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.SneakyThrows;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

// @formatter:off
/**
 * AWS SDK v2 metrics decorator for {@link AmazonSnsConsumer}. Records publish attempt/success/failure
 * counters, latency, batch size, and inflight gauges using Micrometer. Handles
 * {@link AwsServiceException} error codes for failure tagging.
 */
public class AmazonSnsConsumerMetricsDecorator extends AbstractAmazonSnsConsumerMetricsDecorator<PublishBatchRequest, PublishBatchResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AmazonSnsConsumerMetricsDecorator.class);

  /**
   * Creates a new v2 SNS consumer metrics decorator.
   *
   * @param delegate      the consumer to decorate
   * @param topicProperty the topic configuration
   * @param meterRegistry the Micrometer meter registry
   */
  public AmazonSnsConsumerMetricsDecorator(
      final AmazonSnsConsumer<PublishBatchRequest, PublishBatchResponse> delegate,
      final TopicProperty topicProperty,
      final MeterRegistry meterRegistry) {
    super(delegate, topicProperty, meterRegistry);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SneakyThrows
  public PublishBatchResponse publish(final PublishBatchRequest publishBatchRequest) {
    publishAttemptsCounter.increment();
    batchSizeSummary.record(publishBatchRequest.publishBatchRequestEntries().size());
    inflightGauge.incrementAndGet();

    try {
      return publishTimer.recordCallable(() -> delegate.publish(publishBatchRequest));
    } finally {
      inflightGauge.decrementAndGet();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handleResponse(final PublishBatchResponse publishBatchResult) {
    delegate.handleResponse(publishBatchResult);

    final int successCount = publishBatchResult.successful().size();
    final int failureCount = publishBatchResult.failed().size();

    if (successCount > 0) {
      successCounter.increment(successCount);
    }

    publishBatchResult.failed().forEach(entry -> failureCounter(entry.code(), ERROR_TYPE_AMAZON).increment());

    if (failureCount > 0) {
      LOGGER.warn("SNS batch partially failed: {} succeeded, {} failed", successCount, failureCount);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handleError(final PublishBatchRequest publishBatchRequest, final Throwable throwable) {
    delegate.handleError(publishBatchRequest, throwable);

    final String errorCode = throwable instanceof AwsServiceException ? AwsServiceException.class.cast(throwable).awsErrorDetails().errorCode() : "000";

    final String errorType = throwable instanceof AwsServiceException ? ERROR_TYPE_AMAZON : ERROR_TYPE_OTHER;

    final int failedEntries = publishBatchRequest.publishBatchRequestEntries().size();

    failureCounter(errorCode, errorType).increment(failedEntries);
  }

}
