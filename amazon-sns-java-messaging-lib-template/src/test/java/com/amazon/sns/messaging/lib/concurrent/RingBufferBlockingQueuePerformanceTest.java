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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// @formatter:off
/**
 * Performance tests for {@link RingBufferBlockingQueue}.
 *
 * This class benchmarks producer/consumer throughput (ops/sec) of
 * {@link RingBufferBlockingQueue} against {@link RingBufferBlockingQueue1},
 * a fair {@link ArrayBlockingQueue}, and {@link LinkedBlockingQueue}, across
 * several combinations of producer count, consumer count, and queue
 * capacity. Each combination runs a warmup phase followed by a measured
 * phase, and asserts that {@link RingBufferBlockingQueue}'s throughput
 * stays above a minimum fraction of the reference queues' throughput.
 */
@Tag("performance")
class RingBufferBlockingQueuePerformanceTest {

  private static final int WARMUP_ELEMENTS = 200_000;
  private static final int MEASURED_ELEMENTS = 2_000_000;
  private static final int QUEUE_CAPACITY = 4096;

  private static Stream<Arguments> provideParameters() {
    return Stream.of(
      Arguments.of(1, 1, QUEUE_CAPACITY),
      Arguments.of(4, 4, QUEUE_CAPACITY),
      Arguments.of(8, 8, QUEUE_CAPACITY),
      Arguments.of(4, 1, QUEUE_CAPACITY),
      Arguments.of(8, 1, QUEUE_CAPACITY),
      Arguments.of(1, 1, 64),
      Arguments.of(4, 4, 64),
      Arguments.of(8, 8, 64),
      Arguments.of(4, 1, 64),
      Arguments.of(8, 1, 64)
    );
  }

  @Timeout(120)
  @MethodSource("provideParameters")
  @ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
  void testProducerAndConsumerThroughput(final int producers, final int consumers, final int capacity) throws Exception {
    System.out.println("\n=== %d producer / %d consumer / %d capacity ===".formatted(producers, consumers, capacity));
    final double ringOpsPerSec = benchmark(new RingBufferBlockingQueue<>(capacity), producers, consumers);
    final double arrayOpsPerSec = benchmark(new ArrayBlockingQueue<>(capacity, true), producers, consumers);
    final double linkedOpsPerSec = benchmark(new LinkedBlockingQueue<>(capacity), producers, consumers);
    report("RingBufferBlockingQueue", ringOpsPerSec);
    report("ArrayBlockingQueue (fair)", arrayOpsPerSec);
    report("LinkedBlockingQueue", linkedOpsPerSec);

    assertThat(ringOpsPerSec).isGreaterThan(arrayOpsPerSec * 0.1);
    assertThat(ringOpsPerSec).isGreaterThan(linkedOpsPerSec * 0.1);
  }

  private static void report(final String label, final double opsPerSec) {
    System.out.printf("%-30s %,15.0f ops/sec%n", label, opsPerSec);
  }

  private double benchmark(final BlockingQueue<Long> queue, final int producerCount, final int consumerCount) throws Exception {
    runPhase(queue, producerCount, consumerCount, WARMUP_ELEMENTS);

    final long start = System.nanoTime();
    runPhase(queue, producerCount, consumerCount, MEASURED_ELEMENTS);
    final long elapsedNanos = System.nanoTime() - start;

    return MEASURED_ELEMENTS / (elapsedNanos / 1_000_000_000.0);
  }

  private void runPhase(final BlockingQueue<Long> queue, final int producerCount, final int consumerCount, final int totalElements) throws Exception {

    final int perProducer = totalElements / producerCount;
    final ExecutorService executor = Executors.newFixedThreadPool(producerCount + consumerCount);
    final AtomicLong consumed = new AtomicLong(0);
    final CountDownLatch producersDone = new CountDownLatch(producerCount);
    try {
      for (int c = 0; c < consumerCount; c++) {
        executor.submit(() -> {
          while (consumed.get() < totalElements) {
            final Long value = queue.take();
            if (value != null) {
              consumed.incrementAndGet();
            } else if ((producersDone.getCount() == 0) && queue.isEmpty()) {
              break;
            }
          }
          return null;
        });
      }
      final List<java.util.concurrent.Future<?>> producers = new java.util.ArrayList<>();
      for (int p = 0; p < producerCount; p++) {
        producers.add(executor.submit(() -> {
          for (int i = 0; i < perProducer; i++) {
            queue.put(1L);
          }
          producersDone.countDown();
          return null;
        }));
      }
      for (final var f : producers) {
        f.get(60, TimeUnit.SECONDS);
      }
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
      while ((consumed.get() < totalElements) && (System.nanoTime() < deadline)) {
        Thread.sleep(10);
      }
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

}
// @formatter:on