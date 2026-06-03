package com.amazon.sns.messaging.lib.metrics;

import java.util.Collection;
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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

// @formatter:off
/**
 * A decorator around {@link ExecutorService} that collects Micrometer metrics for
 * task execution, including active task count, succeeded/failed counters, and
 * task duration histograms.
 */
public class ExecutorServiceMetricsDecorator implements ExecutorService {

  /** Base tag prefix for executor metrics. */
  private static final String TAG_EXECUTOR = "executor";

  /** Metric name for active task count gauge. */
  private static final String METRIC_ACTIVE = TAG_EXECUTOR.concat(".active");

  /** Metric name for succeeded task counter. */
  private static final String METRIC_TASKS_SUCCEEDED = TAG_EXECUTOR.concat(".tasks.succeeded");

  /** Metric name for failed task counter. */
  private static final String METRIC_TASKS_FAILED = TAG_EXECUTOR.concat(".tasks.failed");

  /** Metric name for task duration timer. */
  private static final String METRIC_TASK_DURATION = TAG_EXECUTOR.concat(".task.duration");

  /** The decorated executor service. */
  private final ExecutorService delegate;

  /** Atomic gauge tracking the number of active tasks. */
  private final AtomicInteger activeTaskCount = new AtomicInteger();

  /** Counter for tasks that completed without throwing an exception. */
  private final Counter succeededCounter;

  /** Counter for tasks that completed by throwing an exception. */
  private final Counter failedCounter;

  /** Timer for wall-clock duration of task execution. */
  private final Timer taskTimer;

  /**
   * Creates a new metrics decorator for the given executor service.
   *
   * @param delegate       the executor service to decorate
   * @param registry       the Micrometer meter registry
   * @param executorName   the name tag for the metrics
   */
  public ExecutorServiceMetricsDecorator(
      final ExecutorService delegate,
      final MeterRegistry registry,
      final String executorName) {

    this.delegate = delegate;

    final CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();

    Optional.ofNullable(registry).ifPresent(compositeMeterRegistry::add);

    final Tags tags = Tags.of("name", executorName);

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

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<?> submit(final Runnable task) {
    return delegate.submit(task);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Future<T> submit(final Runnable task, final T result) {
    return delegate.submit(task, result);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Future<T> submit(final Callable<T> task) {
    return delegate.submit(task);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return delegate.invokeAny(tasks);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(tasks, timeout, unit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return delegate.invokeAll(tasks);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException {
    return delegate.invokeAll(tasks, timeout, unit);
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
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  /**
   * Delegates execution to the decorated executor, wrapping the command with
   * metrics tracking for active count, success/failure counters, and duration.
   *
   * @param command the task to execute
   */
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
