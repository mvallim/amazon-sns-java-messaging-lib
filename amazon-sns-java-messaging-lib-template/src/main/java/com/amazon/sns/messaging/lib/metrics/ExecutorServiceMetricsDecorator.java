package com.amazon.sns.messaging.lib.metrics;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

// @formatter:off
public class ExecutorServiceMetricsDecorator implements ExecutorService {

  private static final String TAG_EXECUTOR = "executor";

  private static final String METRIC_ACTIVE = TAG_EXECUTOR.concat(".active");

  private static final String METRIC_TASKS_SUCCEEDED = TAG_EXECUTOR.concat(".tasks.succeeded");

  private static final String METRIC_TASKS_FAILED = TAG_EXECUTOR.concat(".tasks.failed");

  private static final String METRIC_TASK_DURATION = TAG_EXECUTOR.concat(".task.duration");

  private final ExecutorService delegate;

  private final AtomicInteger activeTaskCount = new AtomicInteger();

  private final Counter succeededCounter;

  private final Counter failedCounter;

  private final Timer taskTimer;

  public ExecutorServiceMetricsDecorator(
      final ExecutorService delegate,
      final MeterRegistry registry,
      final String executorName) {

    this.delegate = delegate;

    final CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();

    Optional.ofNullable(registry).ifPresent(compositeMeterRegistry::add);

    final List<Tag> tags = new LinkedList<>(Collections.singleton(Tag.of("name", executorName)));

    Gauge.builder(METRIC_ACTIVE, activeTaskCount, AtomicInteger::get)
      .tags(tags)
      .description("Number of tasks currently being executed by pool threads")
      .register(compositeMeterRegistry);

    succeededCounter = Counter.builder(METRIC_TASKS_SUCCEEDED)
      .tags(tags)
      .description("Total number of tasks that completed without throwing an exception")
      .register(compositeMeterRegistry);

    failedCounter = Counter.builder(METRIC_TASKS_FAILED)
      .tags(tags)
      .description("Total number of tasks that completed by throwing an exception")
      .register(compositeMeterRegistry);

    taskTimer = Timer.builder(METRIC_TASK_DURATION)
      .tags(tags)
      .description("Wall-clock duration of each task execution, from beforeExecute to afterExecute")
      .register(compositeMeterRegistry);
  }

  @Override
  public Future<?> submit(final Runnable task) {
    return delegate.submit(task);
  }

  @Override
  public <T> Future<T> submit(final Runnable task, final T result) {
    return delegate.submit(task, result);
  }

  @Override
  public <T> Future<T> submit(final Callable<T> task) {
    return delegate.submit(task);
  }

  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return delegate.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(tasks, timeout, unit);
  }

  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return delegate.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException {
    return delegate.invokeAll(tasks, timeout, unit);
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public void execute(final Runnable command) {
    delegate.execute(() -> {
      activeTaskCount.incrementAndGet();

      final Timer.Sample sample = Timer.start();

      try {
        command.run();

        succeededCounter.increment();
      }
      catch (final Exception ex) {
        failedCounter.increment();
        throw ex;
      }
      finally {
        sample.stop(taskTimer);
        activeTaskCount.decrementAndGet();
      }
    });
  }

}
