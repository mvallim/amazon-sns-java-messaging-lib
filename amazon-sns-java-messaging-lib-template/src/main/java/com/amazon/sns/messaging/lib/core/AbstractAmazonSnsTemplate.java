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

package com.amazon.sns.messaging.lib.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.amazon.sns.messaging.lib.instrument.AmazonSnsThreadPoolExecutorJmx;
import com.amazon.sns.messaging.lib.instrument.MBeanRegistrar;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

// @formatter:off
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractAmazonSnsTemplate<C, R, O, E> {

  private final AbstractAmazonSnsProducer<E> amazonSnsProducer;

  private final AbstractAmazonSnsConsumer<C, R, O, E> amazonSnsConsumer;

  public ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> send(final RequestEntry<E> requestEntry) {
    return this.amazonSnsProducer.send(requestEntry);
  }

  public void shutdown() {
    this.amazonSnsProducer.shutdown();
    this.amazonSnsConsumer.shutdown();
  }

  public CompletableFuture<Void> await() {
    return this.amazonSnsConsumer.await();
  }

  @SuppressWarnings("java:S1602")
  protected static AmazonSnsThreadPoolExecutor getAmazonSnsThreadPoolExecutor(final TopicProperty topicProperty) {
    final Supplier<AmazonSnsThreadPoolExecutor> supplier = () -> {
      return topicProperty.isFifo() ? new AmazonSnsThreadPoolExecutor(1) : new AmazonSnsThreadPoolExecutor(topicProperty.getMaximumPoolSize());
    };

    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = supplier.get();

    final String topicArn = topicProperty.getTopicArn();
    final String topicName = topicArn.substring(topicArn.lastIndexOf(':') + 1);
    final String name = String.format("com.amazon.sns.messaging.lib:type=AmazonSnsThreadPoolExecutor,name=%s", topicName);

    MBeanRegistrar.registerMBean(new AmazonSnsThreadPoolExecutorJmx(amazonSnsThreadPoolExecutor), name);

    return amazonSnsThreadPoolExecutor;
  }

}
// @formatter:on
