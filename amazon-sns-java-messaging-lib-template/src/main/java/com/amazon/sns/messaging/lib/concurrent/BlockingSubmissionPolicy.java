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

package com.amazon.sns.messaging.lib.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.SneakyThrows;

/**
 * A {@link RejectedExecutionHandler} that blocks the caller thread until the task can be
 * enqueued, up to the specified timeout. If the timeout elapses, a {@link RejectedExecutionException}
 * is thrown.
 */
public class BlockingSubmissionPolicy implements RejectedExecutionHandler {

  /** The maximum time to wait for queue insertion, in milliseconds. */
  private final long timeout;
  
  /**
   * Creates a new policy with the given blocking timeout.
   *
   * @param timeout the maximum time to wait for queue insertion, in milliseconds
   */
  public BlockingSubmissionPolicy(final long timeout) {
    this.timeout = timeout;
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  @SneakyThrows
  public void rejectedExecution(final Runnable runnable, final ThreadPoolExecutor executor) {
    final BlockingQueue<Runnable> queue = executor.getQueue();
    if (!queue.offer(runnable, timeout, TimeUnit.MILLISECONDS)) {
      throw new RejectedExecutionException("Timeout");
    }
  }
  
}
