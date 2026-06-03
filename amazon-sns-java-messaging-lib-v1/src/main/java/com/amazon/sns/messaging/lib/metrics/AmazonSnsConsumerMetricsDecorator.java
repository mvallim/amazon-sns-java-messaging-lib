package com.amazon.sns.messaging.lib.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sns.messaging.lib.core.AmazonSnsConsumer;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchResult;

import io.micrometer.core.instrument.MeterRegistry;

// @formatter:off
/**
 * AWS SDK v1 metrics decorator for {@link AmazonSnsConsumer}. Records publish attempt/success/failure
 * counters, latency, batch size, and inflight gauges using Micrometer. Handles
 * {@link AmazonServiceException} error codes for failure tagging.
 */
public class AmazonSnsConsumerMetricsDecorator extends AbstractAmazonSnsConsumerMetricsDecorator<PublishBatchRequest, PublishBatchResult> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AmazonSnsConsumerMetricsDecorator.class);

  /**
   * Creates a new v1 SNS consumer metrics decorator.
   *
   * @param delegate      the consumer to decorate
   * @param topicProperty the topic configuration
   * @param meterRegistry the Micrometer meter registry
   */
  public AmazonSnsConsumerMetricsDecorator(
      final AmazonSnsConsumer<PublishBatchRequest, PublishBatchResult> delegate,
      final TopicProperty topicProperty,
      final MeterRegistry meterRegistry) {
    super(delegate, topicProperty, meterRegistry);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PublishBatchResult publish(final PublishBatchRequest publishBatchRequest) {
    publishAttemptsCounter.increment();
    batchSizeSummary.record(publishBatchRequest.getPublishBatchRequestEntries().size());
    inflightGauge.incrementAndGet();

    try {
      return publishTimer.recordCallable(() -> delegate.publish(publishBatchRequest));
    } catch (final RuntimeException ex) {
      throw ex;
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    } finally {
      inflightGauge.decrementAndGet();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handleResponse(final PublishBatchResult publishBatchResult) {
    delegate.handleResponse(publishBatchResult);

    final int successCount = publishBatchResult.getSuccessful().size();
    final int failureCount = publishBatchResult.getFailed().size();

    if (successCount > 0) {
      successCounter.increment(successCount);
    }

    publishBatchResult.getFailed().forEach(entry -> failureCounter(entry.getCode(), ERROR_TYPE_AMAZON).increment());

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

    final String errorCode = throwable instanceof AmazonServiceException ? AmazonServiceException.class.cast(throwable).getErrorCode() : "000";

    final String errorType = throwable instanceof AmazonServiceException ? ERROR_TYPE_AMAZON : ERROR_TYPE_OTHER;

    final int failedEntries = publishBatchRequest.getPublishBatchRequestEntries().size();

    failureCounter(errorCode, errorType).increment(failedEntries);
  }

}
