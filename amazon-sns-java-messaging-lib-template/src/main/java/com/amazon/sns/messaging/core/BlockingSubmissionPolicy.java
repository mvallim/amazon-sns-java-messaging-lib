package com.amazon.sns.messaging.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class BlockingSubmissionPolicy implements RejectedExecutionHandler {

  private final long timeout;

  public BlockingSubmissionPolicy(final long timeout) {
    this.timeout = timeout;
  }

  @Override
  public void rejectedExecution(final Runnable runnable, final ThreadPoolExecutor executor) {
    try {
      final BlockingQueue<Runnable> queue = executor.getQueue();
      if (!queue.offer(runnable, timeout, TimeUnit.MILLISECONDS)) {
        throw new RejectedExecutionException("Timeout");
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}
