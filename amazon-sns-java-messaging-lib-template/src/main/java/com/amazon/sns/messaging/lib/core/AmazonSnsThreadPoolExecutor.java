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

package com.amazon.sns.messaging.lib.core;

import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AmazonSnsThreadPoolExecutor extends ThreadPoolExecutor {

  private final Object lockObject = new Object();

  private int activeTaskCount = 0;

  private int failedTaskCount = 0;

  private int succeededTaskCount = 0;

  public AmazonSnsThreadPoolExecutor(final int maximumPoolSize) {
    super(2, maximumPoolSize, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), new BlockingSubmissionPolicy(30000));
  }

  public int getActiveTaskCount() {
    synchronized (lockObject) {
      return activeTaskCount;
    }
  }

  public int getFailedTaskCount() {
    synchronized (lockObject) {
      return failedTaskCount;
    }
  }

  public int getSucceededTaskCount() {
    synchronized (lockObject) {
      return succeededTaskCount;
    }
  }

  @Override
  protected void beforeExecute(final Thread thread, final Runnable runnable) {
    synchronized (lockObject) {
      try {
        super.beforeExecute(thread, runnable);
      } finally {
        activeTaskCount++;
      }
    }
  }

  @Override
  protected void afterExecute(final Runnable runnable, final Throwable throwable) {
    synchronized (lockObject) {
      try {
        super.afterExecute(runnable, throwable);
      } finally {
        if (Objects.nonNull(throwable)) {
          failedTaskCount++;
        } else {
          succeededTaskCount++;
        }
        activeTaskCount--;
      }
    }
  }

}
