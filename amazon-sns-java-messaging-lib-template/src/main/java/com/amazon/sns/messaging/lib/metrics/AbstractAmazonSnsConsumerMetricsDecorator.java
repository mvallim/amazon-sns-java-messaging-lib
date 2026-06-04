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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.amazon.sns.messaging.lib.core.AmazonSnsConsumer;
import com.amazon.sns.messaging.lib.model.TopicProperty;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

// @formatter:off
/**
 * Abstract base class for decorating an {@link AmazonSnsConsumer} with Micrometer metrics.
 * Tracks publish attempts, successes, failures, latency, batch size, and inflight counts.
 *
 * @param <I> the publish batch request type
 * @param <O> the publish batch result type
 */
abstract class AbstractAmazonSnsConsumerMetricsDecorator<I, O> implements AmazonSnsConsumer<I, O> {

  /** Base tag prefix for all SNS metrics. */
  private static final String TAG_SNS = "sns";

  /** Metric name for total publish attempts. */
  protected static final String METRIC_PUBLISH_ATTEMPTS = TAG_SNS.concat(".publish.attempts");

  /** Metric name for successful publish operations. */
  protected static final String METRIC_PUBLISH_SUCCESS = TAG_SNS.concat(".publish.success");

  /** Metric name for failed publish operations. */
  protected static final String METRIC_PUBLISH_FAILURE = TAG_SNS.concat(".publish.failure");

  /** Metric name for publish latency duration. */
  protected static final String METRIC_PUBLISH_DURATION = TAG_SNS.concat(".publish.duration");

  /** Metric name for publish batch size distribution. */
  protected static final String METRIC_PUBLISH_BATCH_SIZE = TAG_SNS.concat(".publish.batch.size");

  /** Metric name for inflight publish count. */
  protected static final String METRIC_PUBLISH_INFLIGHT = TAG_SNS.concat(".publish.inflight");

  /** Tag key for error code dimension. */
  protected static final String TAG_ERROR_CODE = "error_code";

  /** Tag key for error type dimension. */
  protected static final String TAG_ERROR_TYPE = "error_type";

  /** Error type value for Amazon service exceptions. */
  protected static final String ERROR_TYPE_AMAZON = "amazon_service_exception";

  /** Error type value for unknown exceptions. */
  protected static final String ERROR_TYPE_OTHER = "unknown";

  /** The composite Micrometer meter registry. */
  protected final MeterRegistry registry;

  /** Metric tags identifying the SNS topic. */
  protected final Tags tags;

  /** Counter for total publish attempts. */
  protected final Counter publishAttemptsCounter;

  /** Counter for successful publishes. */
  protected final Counter successCounter;

  /** Timer for publish latency. */
  protected final Timer publishTimer;

  /** Distribution summary for batch sizes. */
  protected final DistributionSummary batchSizeSummary;

  /** Atomic gauge tracking the number of inflight publish operations. */
  protected final AtomicInteger inflightGauge = new AtomicInteger();

  /** The decorated {@link AmazonSnsConsumer} delegate. */
  protected final AmazonSnsConsumer<I, O> delegate;

  /**
   * Creates a new metrics decorator.
   *
   * @param delegate      the consumer to decorate
   * @param topicProperty the topic configuration (used for topic tags)
   * @param meterRegistry the Micrometer meter registry (may be null)
   */
  AbstractAmazonSnsConsumerMetricsDecorator(
     final AmazonSnsConsumer<I, O> delegate,
     final TopicProperty topicProperty,
     final MeterRegistry meterRegistry) {

    this.delegate = delegate;

    final CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();

    Optional.ofNullable(meterRegistry).ifPresent(compositeMeterRegistry::add);

    registry = compositeMeterRegistry;

    tags = Tags.of("topic", topicProperty.getTopicArn());

    publishAttemptsCounter = Counter.builder(METRIC_PUBLISH_ATTEMPTS)
      .tags(tags)
      .description("Total number of SNS PublishBatch calls attempted")
      .register(compositeMeterRegistry);

    successCounter = Counter.builder(METRIC_PUBLISH_SUCCESS)
      .tags(tags)
      .description("Individual SNS messages acknowledged as successful")
      .register(compositeMeterRegistry);

    publishTimer = Timer.builder(METRIC_PUBLISH_DURATION)
      .tags(tags)
      .description("End-to-end latency of SNS PublishBatch calls")
      .publishPercentiles(0.5, 0.95, 0.99)
      .register(compositeMeterRegistry);

    batchSizeSummary = DistributionSummary.builder(METRIC_PUBLISH_BATCH_SIZE)
      .tags(tags)
      .description("Number of entries per SNS PublishBatch request")
      .register(compositeMeterRegistry);

    Gauge.builder(METRIC_PUBLISH_INFLIGHT, inflightGauge, AtomicInteger::get)
      .tags(tags)
      .description("PublishBatches currently in progress")
      .register(compositeMeterRegistry);
  }

  /**
   * Returns (creating if necessary) a failure counter tagged with the given error code and type.
   *
   * @param errorCode the error code for the failure tag
   * @param errorType the error type for the failure tag
   * @return the failure counter
   */
  protected Counter failureCounter(final String errorCode, final String errorType) {
    return Counter.builder(METRIC_PUBLISH_FAILURE)
      .description("Individual SNS messages that failed to be published")
      .tags(tags.and(TAG_ERROR_CODE, errorCode)
      .and(TAG_ERROR_TYPE, errorType))
      .register(registry);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletableFuture<Void> await() {
    return delegate.await();
  }

}
