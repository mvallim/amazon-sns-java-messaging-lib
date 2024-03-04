/*
 * Copyright 2024 the original author or authors.
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

package com.amazon.sns.messaging.lib.core.jmh;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import com.amazon.sns.messaging.lib.core.AmazonSnsTemplate;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchResult;
import com.amazonaws.services.sns.model.PublishBatchResultEntry;

// @formatter:off
@Fork(5)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class AmazonSnsTemplateBenchmark {

  private AmazonSnsTemplate<Integer> amazonSnsTemplate;

  @Setup
  public void setup() {
    final AmazonSNS amazonSNS = mock(AmazonSNS.class);

    when(amazonSNS.publishBatch(any())).thenAnswer(invocation -> {
      final PublishBatchRequest request = invocation.getArgument(0, PublishBatchRequest.class);
      final List<PublishBatchResultEntry> resultEntries = request.getPublishBatchRequestEntries().stream()
        .map(entry -> new PublishBatchResultEntry().withId(entry.getId()))
        .collect(Collectors.toList());
      return new PublishBatchResult().withSuccessful(resultEntries);
    });

    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(false)
      .linger(70)
      .maxBatchSize(10)
      .maximumPoolSize(512)
      .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
      .build();

    amazonSnsTemplate = new AmazonSnsTemplate<>(amazonSNS, topicProperty);
  }

  @TearDown
  public void tearDown() {
    amazonSnsTemplate.await().join();
    amazonSnsTemplate.shutdown();
  }

  @Benchmark
  @OperationsPerInvocation(1)
  public void testSend_1() throws InterruptedException {
    amazonSnsTemplate.send(RequestEntry.<Integer>builder().withValue(1).build());
  }

  @Benchmark
  @OperationsPerInvocation(10)
  public void testSend_10() throws InterruptedException {
    for (int i = 0; i < 10; i++) {
      amazonSnsTemplate.send(RequestEntry.<Integer>builder().withValue(i).build());
    }
  }

  @Benchmark
  @OperationsPerInvocation(100)
  public void testSend_100() throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      amazonSnsTemplate.send(RequestEntry.<Integer>builder().withValue(i).build());
    }
  }

  @Benchmark
  @OperationsPerInvocation(1000)
  public void testSend_1000() throws InterruptedException {
    for (int i = 0; i < 1000; i++) {
      amazonSnsTemplate.send(RequestEntry.<Integer>builder().withValue(i).build());
    }
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  public void testSend_10000() throws InterruptedException {
    for (int i = 0; i < 10000; i++) {
      amazonSnsTemplate.send(RequestEntry.<Integer>builder().withValue(i).build());
    }
  }

}
// @formatter:on
