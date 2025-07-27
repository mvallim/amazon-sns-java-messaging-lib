/*
 * Copyright 2025 the original author or authors.
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

package com.amazon.sns.messaging.lib.instrument;

import com.amazon.sns.messaging.lib.core.AmazonSnsThreadPoolExecutor;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AmazonSnsThreadPoolExecutorJmx implements AmazonSnsThreadPoolExecutorJmxMBean {

  private final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor;

  @Override
  public int getActiveTaskCount() {
    return this.amazonSnsThreadPoolExecutor.getActiveTaskCount();
  }

  @Override
  public int getFailedTaskCount() {
    return this.amazonSnsThreadPoolExecutor.getFailedTaskCount();
  }

  @Override
  public int getSucceededTaskCount() {
    return this.amazonSnsThreadPoolExecutor.getSucceededTaskCount();
  }

  @Override
  public int getQueueSize() {
    return this.amazonSnsThreadPoolExecutor.getQueueSize();
  }

  @Override
  public int getPoolSize() {
    return this.amazonSnsThreadPoolExecutor.getPoolSize();
  }
  
}
