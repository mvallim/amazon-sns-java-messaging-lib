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

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

// @formatter:off
/**
 * A decorator around {@link BlockingQueue} that collects Micrometer metrics for
 * put/take operations, including counters, latency histograms, and queue size gauges.
 *
 * @param <E> the type of elements held in the queue
 */
public class BlockingQueueMetricsDecorator<E> implements BlockingQueue<E> {

  /** Base tag prefix for blocking queue metrics. */
  private static final String TAG_QUEUE = "blocking.queue";

  /** Metric name for total put operations. */
  private static final String METRIC_PUTS_TOTAL = TAG_QUEUE.concat(".puts.total");

  /** Metric name for failed put operations. */
  private static final String METRIC_PUTS_FAILED = TAG_QUEUE.concat(".puts.failed");

  /** Metric name for put operation latency. */
  private static final String METRIC_PUT_DURATION = TAG_QUEUE.concat(".put.duration");

  /** Metric name for total take operations. */
  private static final String METRIC_TAKES_TOTAL = TAG_QUEUE.concat(".takes.total");

  /** Metric name for failed take operations. */
  private static final String METRIC_TAKES_FAILED = TAG_QUEUE.concat(".takes.failed");

  /** Metric name for take operation latency. */
  private static final String METRIC_TAKE_DURATION = TAG_QUEUE.concat(".take.duration");

  /** Metric name for queue size gauge. */
  private static final String METRIC_SIZE = TAG_QUEUE.concat(".size");

  /** The decorated blocking queue. */
  private final BlockingQueue<E> delegate;

  /** Counter for successful put operations. */
  private final Counter putsTotal;

  /** Counter for put operations that threw an exception. */
  private final Counter putsFailed;

  /** Timer for put operation latency. */
  private final Timer putDuration;

  /** Counter for successful take operations. */
  private final Counter takesTotal;

  /** Counter for take operations that threw an exception. */
  private final Counter takesFailed;

  /** Timer for take operation latency. */
  private final Timer takeDuration;

  /**
   * Creates a new metrics decorator for the given blocking queue.
   *
   * @param delegate  the blocking queue to decorate
   * @param registry  the Micrometer meter registry
   * @param queueName the name tag for the metrics
   */
  public BlockingQueueMetricsDecorator(
      final BlockingQueue<E> delegate,
      final MeterRegistry registry,
      final String queueName) {

    this.delegate = delegate;

    final CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();

    Optional.ofNullable(registry).ifPresent(compositeMeterRegistry::add);

    final Tags tags = Tags.of("name", queueName);

    putsTotal = Counter.builder(METRIC_PUTS_TOTAL)
      .description("Total number of successful put operations")
      .tags(tags)
      .register(compositeMeterRegistry);

    putsFailed = Counter.builder(METRIC_PUTS_FAILED)
      .description("Total number of put operations that threw an exception")
      .tags(tags)
      .register(compositeMeterRegistry);

    takesTotal = Counter.builder(METRIC_TAKES_TOTAL)
      .description("Total number of successful take operations")
      .tags(tags)
      .register(compositeMeterRegistry);

    takesFailed = Counter.builder(METRIC_TAKES_FAILED)
      .description("Total number of take operations that threw an exception")
      .tags(tags)
      .register(compositeMeterRegistry);

    putDuration = Timer.builder(METRIC_PUT_DURATION)
      .description("Latency of put operations (including wait time when queue is full)")
      .tags(tags)
      .publishPercentileHistogram()
      .register(compositeMeterRegistry);

    takeDuration = Timer.builder(METRIC_TAKE_DURATION)
      .description("Latency of take operations (including wait time when queue is empty)")
      .tags(tags)
      .publishPercentileHistogram()
      .register(compositeMeterRegistry);

    Gauge.builder(METRIC_SIZE, this.delegate, BlockingQueue::size)
      .description("Current number of elements in the queue")
      .tags(tags)
      .register(compositeMeterRegistry);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void put(final E element) throws InterruptedException {
    final Timer.Sample sample = Timer.start();
    try {
      delegate.put(element);
      putsTotal.increment();
    } catch (final Exception ex) {
      putsFailed.increment();
      throw ex;
    } finally {
      sample.stop(putDuration);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E take() throws InterruptedException {
    final Timer.Sample sample = Timer.start();
    try {
      final E value = delegate.take();
      takesTotal.increment();
      return value;
    } catch (final Exception ex) {
      takesFailed.increment();
      throw ex;
    } finally {
      sample.stop(takeDuration);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size() {
    return delegate.size();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E peek() {
    return delegate.peek();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean offer(final E element) {
    return delegate.offer(element);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean offer(final E element, final long timeout, final TimeUnit unit) throws InterruptedException {
    return delegate.offer(element, timeout, unit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E poll() {
    return delegate.poll();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E poll(final long timeout, final TimeUnit unit) throws InterruptedException {
    return delegate.poll(timeout, unit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Iterator<E> iterator() {
    return delegate.iterator();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean add(final E element) {
    return delegate.add(element);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int remainingCapacity() {
    return delegate.remainingCapacity();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int drainTo(final Collection<? super E> collection) {
    return delegate.drainTo(collection);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int drainTo(final Collection<? super E> collection, final int maxElements) {
    return delegate.drainTo(collection, maxElements);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E remove() {
    return delegate.remove();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E element() {
    return delegate.element();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object[] toArray() {
    return delegate.toArray();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T[] toArray(final T[] a) {
    return delegate.toArray(a);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean containsAll(final Collection<?> c) {
    return delegate.containsAll(c);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean addAll(final Collection<? extends E> c) {
    return delegate.addAll(c);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean removeAll(final Collection<?> c) {
    return delegate.removeAll(c);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean retainAll(final Collection<?> c) {
    return delegate.retainAll(c);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clear() {
    delegate.clear();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean remove(final Object o) {
    return delegate.remove(o);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean contains(final Object o) {
    return delegate.contains(o);
  }

}