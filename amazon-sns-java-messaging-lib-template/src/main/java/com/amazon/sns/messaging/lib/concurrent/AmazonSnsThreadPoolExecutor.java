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

package com.amazon.sns.messaging.lib.concurrent;

import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A custom {@link ThreadPoolExecutor} that tracks active, failed, and succeeded task counts.
 * Uses a blocking submission policy to handle rejection and a synchronous queue for task handoff.
 */
public class AmazonSnsThreadPoolExecutor extends ThreadPoolExecutor {

  private final AtomicInteger activeTaskCount = new AtomicInteger();

  private final AtomicInteger failedTaskCount = new AtomicInteger();

  private final AtomicInteger succeededTaskCount = new AtomicInteger();

  /**
   * Creates a new executor with the specified maximum pool size.
   *
   * @param maximumPoolSize the maximum number of threads to allow in the pool
   */
  public AmazonSnsThreadPoolExecutor(final int maximumPoolSize) {
    super(0, maximumPoolSize, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), ThreadFactoryProvider.getThreadFactory(), new BlockingSubmissionPolicy(30000));
  }

  /**
   * Returns the number of currently active tasks.
   *
   * @return the active task count
   */
  public int getActiveTaskCount() {
    return activeTaskCount.get();
  }

  /**
   * Returns the number of tasks that have failed.
   *
   * @return the failed task count
   */
  public int getFailedTaskCount() {
    return failedTaskCount.get();
  }

  /**
   * Returns the number of tasks that have completed successfully.
   *
   * @return the succeeded task count
   */
  public int getSucceededTaskCount() {
    return succeededTaskCount.get();
  }

  /**
   * Returns the current size of the task queue.
   *
   * @return the queue size
   */
  public int getQueueSize() {
    return getQueue().size();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void beforeExecute(final Thread thread, final Runnable runnable) {
    try {
      super.beforeExecute(thread, runnable);
    } finally {
      activeTaskCount.incrementAndGet();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void afterExecute(final Runnable runnable, final Throwable throwable) {
    try {
      super.afterExecute(runnable, throwable);
    } finally {
      if (Objects.nonNull(throwable)) {
        failedTaskCount.incrementAndGet();
      } else {
        succeededTaskCount.incrementAndGet();
      }
      activeTaskCount.decrementAndGet();
    }
  }

}
