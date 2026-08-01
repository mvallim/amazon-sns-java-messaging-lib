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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Concurrency tests for {@link RingBufferBlockingQueue}.
 *
 * This class verifies that the blocking queue behaves correctly under
 * concurrent access, covering: - blocking/unblocking semantics of put() and
 * take() when the queue is full or empty; - correct handling of thread
 * interruption while blocked in put() or take(), ensuring the queue remains
 * usable afterwards; - correctness under multiple concurrent producers and
 * consumers, guaranteeing that every element is delivered exactly once with no
 * loss or duplication; - that size() never reports a value outside the valid
 * [0, capacity] range while producers and consumers are running concurrently; -
 * stability and correctness under high contention with a minimal-capacity
 * queue.
 */
class RingBufferBlockingQueueConcurrencyTest {

  private final List<ExecutorService> executors = new ArrayList<>();

  @AfterEach
  void tearDownExecutors() {
    executors.forEach(ExecutorService::shutdownNow);
    executors.clear();
  }

  private ExecutorService newExecutor(final int threads) {
    final ExecutorService executorService = Executors.newFixedThreadPool(threads);
    executors.add(executorService);
    return executorService;
  }

  private static <T> T getResult(final Future<T> future, final long timeoutSeconds) throws Exception {
    try {
      return future.get(timeoutSeconds, TimeUnit.SECONDS);
    } catch (final ExecutionException e) {
      if (e.getCause() instanceof AssertionError) {
        throw (AssertionError) e.getCause();
      }
      throw e;
    }
  }

  @Test
  @Timeout(5)
  void testPutBlocksWhenFullAndResumesAfterTake() throws Exception {
    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(1);
    queue.put(1);

    final CountDownLatch aboutToBlock = new CountDownLatch(1);
    final AtomicBoolean putCompleted = new AtomicBoolean(false);

    final Thread producer = new Thread(() -> {
      aboutToBlock.countDown();
      try {
        queue.put(2);
        putCompleted.set(true);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    producer.start();

    aboutToBlock.await();
    Thread.sleep(200);
    assertFalse(putCompleted.get(), "put() should not complete while the queue is full");

    assertEquals(Integer.valueOf(1), queue.take());
    producer.join(2000);

    assertFalse(producer.isAlive());
    assertTrue(putCompleted.get(), "put() should complete once space becomes available");
    assertEquals(Integer.valueOf(2), queue.take());
  }

  @Test
  @Timeout(5)
  void testTakeBlocksWhenEmptyAndResumesAfterPut() throws Exception {
    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(4);

    final CountDownLatch aboutToBlock = new CountDownLatch(1);
    final AtomicReference<Integer> takenValue = new AtomicReference<>();

    final Thread consumer = new Thread(() -> {
      aboutToBlock.countDown();
      try {
        takenValue.set(queue.take());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    consumer.start();

    aboutToBlock.await();
    Thread.sleep(200);
    assertNull(takenValue.get(), "take() should not complete while the queue is empty");

    queue.put(42);
    consumer.join(2000);

    assertFalse(consumer.isAlive());
    assertEquals(Integer.valueOf(42), takenValue.get());
  }

  @Test
  @Timeout(5)
  void testInterruptingBlockedPutThrowsAndLeavesQueueUsable() throws Exception {
    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(1);
    queue.put(1);

    final CountDownLatch aboutToBlock = new CountDownLatch(1);
    final AtomicReference<Throwable> caught = new AtomicReference<>();

    final Thread producer = new Thread(() -> {
      aboutToBlock.countDown();
      try {
        queue.put(2);
      } catch (final InterruptedException e) {
        caught.set(e);
      }
    });
    producer.start();

    aboutToBlock.await();
    Thread.sleep(200);
    producer.interrupt();
    producer.join(2000);

    assertFalse(producer.isAlive(), "producer thread should have terminated after being interrupted");
    assertTrue(caught.get() instanceof InterruptedException, "InterruptedException should have been thrown");

    assertEquals(Integer.valueOf(1), queue.take());
    queue.put(3);
    assertEquals(Integer.valueOf(3), queue.take());
  }

  @Test
  @Timeout(5)
  void testInterruptingBlockedTakeThrowsAndLeavesQueueUsable() throws Exception {
    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(4);

    final CountDownLatch aboutToBlock = new CountDownLatch(1);
    final AtomicReference<Throwable> caught = new AtomicReference<>();

    final Thread consumer = new Thread(() -> {
      aboutToBlock.countDown();
      try {
        queue.take();
      } catch (final InterruptedException e) {
        caught.set(e);
      }
    });
    consumer.start();

    aboutToBlock.await();
    Thread.sleep(200);
    consumer.interrupt();
    consumer.join(2000);

    assertFalse(consumer.isAlive(), "consumer thread should have terminated after being interrupted");
    assertTrue(caught.get() instanceof InterruptedException, "InterruptedException should have been thrown");

    queue.put(99);
    assertEquals(Integer.valueOf(99), queue.take());
  }

  @Timeout(30)
  @RepeatedTest(3)
  void testMultipleProducersAndConsumersDeliverEachElementExactlyOnce() throws Exception {
    final int capacity = 8;
    final int producerCount = 6;
    final int consumerCount = 4;
    final int elementsPerProducer = 1500;
    final int total = producerCount * elementsPerProducer;
    final Integer poisonPill = Integer.MIN_VALUE;

    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(capacity);
    final AtomicInteger idGenerator = new AtomicInteger(0);
    final AtomicIntegerArray deliveryCount = new AtomicIntegerArray(total);

    final ExecutorService producerPool = newExecutor(producerCount);
    final ExecutorService consumerPool = newExecutor(consumerCount);

    final List<Future<Void>> producerFutures = new ArrayList<>();
    for (int p = 0; p < producerCount; p++) {
      producerFutures.add(producerPool.submit(() -> {
        for (int i = 0; i < elementsPerProducer; i++) {
          queue.put(idGenerator.getAndIncrement());
        }
        return null;
      }));
    }

    final List<Future<Integer>> consumerFutures = new ArrayList<>();
    for (int c = 0; c < consumerCount; c++) {
      consumerFutures.add(consumerPool.submit(() -> {
        int consumed = 0;
        while (true) {
          final Integer value = queue.take();
          if (value.equals(poisonPill)) {
            break;
          }
          final int previous = deliveryCount.getAndIncrement(value);
          assertEquals(0, previous, () -> "element " + value + " delivered more than once");
          consumed++;
        }
        return consumed;
      }));
    }

    for (final Future<Void> f : producerFutures) {
      getResult(f, 20);
    }
    for (int c = 0; c < consumerCount; c++) {
      queue.put(poisonPill);
    }

    int totalConsumed = 0;
    for (final Future<Integer> f : consumerFutures) {
      totalConsumed += getResult(f, 20);
    }

    assertEquals(total, totalConsumed, "total consumed count should match total produced count");
    for (int i = 0; i < total; i++) {
      final int idx = i;
      assertEquals(1, deliveryCount.get(idx), () -> "element " + idx + " was not delivered exactly once");
    }
    assertTrue(queue.isEmpty());
  }

  @Test
  @Timeout(15)
  void testSizeNeverViolatesBoundsUnderConcurrency() throws Exception {
    final int capacity = 16;
    final int workerCount = 4;
    final int elementsPerWorker = 4000;

    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(capacity);
    final AtomicBoolean running = new AtomicBoolean(true);
    final AtomicReference<String> violation = new AtomicReference<>();

    final Thread monitor = new Thread(() -> {
      while (running.get()) {
        final int size = queue.size();
        if ((size < 0) || (size > capacity)) {
          violation.compareAndSet(null, "size=" + size + " capacity=" + capacity);
        }
      }
    });
    monitor.start();

    final ExecutorService producerPool = newExecutor(workerCount);
    final ExecutorService consumerPool = newExecutor(workerCount);
    final AtomicInteger idGenerator = new AtomicInteger(0);

    final List<Future<Void>> producerFutures = new ArrayList<>();
    for (int p = 0; p < workerCount; p++) {
      producerFutures.add(producerPool.submit(() -> {
        for (int i = 0; i < elementsPerWorker; i++) {
          queue.put(idGenerator.getAndIncrement());
        }
        return null;
      }));
    }

    final List<Future<Void>> consumerFutures = new ArrayList<>();
    for (int c = 0; c < workerCount; c++) {
      consumerFutures.add(consumerPool.submit(() -> {
        for (int i = 0; i < elementsPerWorker; i++) {
          queue.take();
        }
        return null;
      }));
    }

    for (final Future<Void> f : producerFutures) {
      getResult(f, 10);
    }
    for (final Future<Void> f : consumerFutures) {
      getResult(f, 10);
    }

    running.set(false);
    monitor.join(2000);

    assertNull(violation.get(), violation.get());
    assertTrue(queue.isEmpty());
  }

  @Timeout(20)
  @RepeatedTest(3)
  void testHighContentionWithMinimalCapacity() throws Exception {
    final int capacity = 1;
    final int producerCount = 8;
    final int consumerCount = 8;
    final int elementsPerProducer = 300;
    final int total = producerCount * elementsPerProducer;
    final Integer poisonPill = Integer.MIN_VALUE;

    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(capacity);
    final AtomicInteger idGenerator = new AtomicInteger(0);
    final AtomicIntegerArray deliveryCount = new AtomicIntegerArray(total);
    final CyclicBarrier startBarrier = new CyclicBarrier(producerCount + consumerCount);

    final ExecutorService producerPool = newExecutor(producerCount);
    final ExecutorService consumerPool = newExecutor(consumerCount);

    final List<Future<Void>> producerFutures = new ArrayList<>();
    for (int p = 0; p < producerCount; p++) {
      producerFutures.add(producerPool.submit(() -> {
        startBarrier.await();
        for (int i = 0; i < elementsPerProducer; i++) {
          queue.put(idGenerator.getAndIncrement());
        }
        return null;
      }));
    }

    final List<Future<Integer>> consumerFutures = new ArrayList<>();
    for (int c = 0; c < consumerCount; c++) {
      consumerFutures.add(consumerPool.submit(() -> {
        startBarrier.await();
        int consumed = 0;
        while (true) {
          final Integer value = queue.take();
          if (value.equals(poisonPill)) {
            break;
          }
          final int previous = deliveryCount.getAndIncrement(value);
          assertEquals(0, previous, () -> "element " + value + " delivered more than once");
          consumed++;
        }
        return consumed;
      }));
    }

    for (final Future<Void> f : producerFutures) {
      getResult(f, 15);
    }
    for (int c = 0; c < consumerCount; c++) {
      queue.put(poisonPill);
    }

    int totalConsumed = 0;
    for (final Future<Integer> f : consumerFutures) {
      totalConsumed += getResult(f, 15);
    }

    assertEquals(total, totalConsumed, "total consumed count should match total produced count");
    for (int i = 0; i < total; i++) {
      final int idx = i;
      assertEquals(1, deliveryCount.get(idx), () -> "element " + idx + " was not delivered exactly once");
    }
    assertTrue(queue.isEmpty());
  }

}