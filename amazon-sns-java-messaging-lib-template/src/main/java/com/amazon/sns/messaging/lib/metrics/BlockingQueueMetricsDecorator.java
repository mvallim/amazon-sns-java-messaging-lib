package com.amazon.sns.messaging.lib.metrics;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

// @formatter:off
public class BlockingQueueMetricsDecorator<E> implements BlockingQueue<E> {

  private static final String TAG_QUEUE = "blocking.queue";

  private static final String METRIC_PUTS_TOTAL = TAG_QUEUE.concat(".puts.total");

  private static final String METRIC_PUTS_FAILED = TAG_QUEUE.concat(".puts.failed");

  private static final String METRIC_PUT_DURATION = TAG_QUEUE.concat(".put.duration");

  private static final String METRIC_TAKES_TOTAL = TAG_QUEUE.concat(".takes.total");

  private static final String METRIC_TAKES_FAILED = TAG_QUEUE.concat(".takes.failed");

  private static final String METRIC_TAKE_DURATION = TAG_QUEUE.concat(".take.duration");

  private static final String METRIC_SIZE = TAG_QUEUE.concat(".size");

  private final BlockingQueue<E> delegate;

  private final Counter putsTotal;

  private final Counter putsFailed;

  private final Timer putDuration;

  private final Counter takesTotal;

  private final Counter takesFailed;

  private final Timer takeDuration;

  public BlockingQueueMetricsDecorator(
      final BlockingQueue<E> delegate,
      final MeterRegistry registry,
      final String queueName) {

    this.delegate = delegate;

    final CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();

    Optional.ofNullable(registry).ifPresent(compositeMeterRegistry::add);

    final List<Tag> tags = new LinkedList<>(Collections.singleton(Tag.of("name", queueName)));

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

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public E peek() {
    return delegate.peek();
  }

  @Override
  public boolean offer(final E element) {
    return delegate.offer(element);
  }

  @Override
  public boolean offer(final E element, final long timeout, final TimeUnit unit) throws InterruptedException {
    return delegate.offer(element, timeout, unit);
  }

  @Override
  public E poll() {
    return delegate.poll();
  }

  @Override
  public E poll(final long timeout, final TimeUnit unit) throws InterruptedException {
    return delegate.poll(timeout, unit);
  }

  @Override
  public Iterator<E> iterator() {
    return delegate.iterator();
  }

  @Override
  public boolean add(final E element) {
    return delegate.add(element);
  }

  @Override
  public int remainingCapacity() {
    return delegate.remainingCapacity();
  }

  @Override
  public int drainTo(final Collection<? super E> collection) {
    return delegate.drainTo(collection);
  }

  @Override
  public int drainTo(final Collection<? super E> collection, final int maxElements) {
    return delegate.drainTo(collection, maxElements);
  }

  @Override
  public E remove() {
    return delegate.remove();
  }

  @Override
  public E element() {
    return delegate.element();
  }

  @Override
  public Object[] toArray() {
    return delegate.toArray();
  }

  @Override
  public <T> T[] toArray(final T[] a) {
    return delegate.toArray(a);
  }

  @Override
  public boolean containsAll(final Collection<?> c) {
    return delegate.containsAll(c);
  }

  @Override
  public boolean addAll(final Collection<? extends E> c) {
    return delegate.addAll(c);
  }

  @Override
  public boolean removeAll(final Collection<?> c) {
    return delegate.removeAll(c);
  }

  @Override
  public boolean retainAll(final Collection<?> c) {
    return delegate.retainAll(c);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public boolean remove(final Object o) {
    return delegate.remove(o);
  }

  @Override
  public boolean contains(final Object o) {
    return delegate.contains(o);
  }

}