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

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// @formatter:off
/**
 * A {@link ThreadPoolExecutor} configured for Amazon SNS publishing. Uses a
 * {@link SynchronousQueue} with zero core threads, allowing threads to be created
 * on demand up to the specified maximum pool size. Tasks that cannot be accepted
 * immediately by the queue will block up to 30 seconds via {@link BlockingSubmissionPolicy}.
 */
public class AmazonSnsThreadPoolExecutor extends ThreadPoolExecutor {

  /**
   * Creates a new thread pool executor with the given maximum pool size.
   *
   * @param maximumPoolSize the maximum number of threads allowed in the pool
   */
  public AmazonSnsThreadPoolExecutor(final int maximumPoolSize) {
    super(0, maximumPoolSize, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), ThreadFactoryProvider.getThreadFactory(), new BlockingSubmissionPolicy(30000));
  }

}
