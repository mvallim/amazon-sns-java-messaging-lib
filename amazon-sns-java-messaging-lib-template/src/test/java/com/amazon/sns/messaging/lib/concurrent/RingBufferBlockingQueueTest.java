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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.model.RequestEntry;

/**
 * Unit tests for {@link RingBufferBlockingQueue}.
 *
 * This class verifies: - constructor validation (rejecting non-positive
 * capacity and rounding capacity up to the next power of two); - basic FIFO
 * put/take behavior, including index wrap-around and peek semantics without
 * removal; - isEmpty()/isFull()/remainingCapacity() state transitions as
 * elements are added and removed; - end-to-end producer/consumer scenarios,
 * including behavior when the queue starts empty, when it starts full, and
 * under sustained high-volume producer/consumer traffic; - that unsupported
 * Queue/BlockingQueue operations correctly throw UnsupportedOperationException;
 * - rejection of null elements passed to put().
 */
class RingBufferBlockingQueueTest {

  @Test
  void testConstructorRejectsNonPositiveCapacity() {
    assertThrows(IllegalArgumentException.class, () -> new RingBufferBlockingQueue<>(0));
    assertThrows(IllegalArgumentException.class, () -> new RingBufferBlockingQueue<>(-5));
  }

  @Test
  void testPutRejectsNullElement() {
    final RingBufferBlockingQueue<String> queue = new RingBufferBlockingQueue<>(4);
    assertThrows(NullPointerException.class, () -> queue.put(null));
  }

  @Test
  void testProducerAndConsumerDeliverAllElementsInOrder() {
    final ExecutorService producer = Executors.newSingleThreadExecutor();

    final ScheduledExecutorService consumer = Executors.newSingleThreadScheduledExecutor(ThreadFactoryProvider.getThreadFactory());

    final List<RequestEntry<Integer>> requestEntriesOut = new LinkedList<>();

    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>(5120);

    producer.submit(() -> {
      IntStream.range(0, 100_000).forEach(value -> {
        try {
          ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(value).build());
        } catch (final InterruptedException e) {
          e.printStackTrace(System.err);
          Thread.currentThread().interrupt();
        }
      });
    });

    consumer.scheduleAtFixedRate(() -> {
      while (!ringBlockingQueue.isEmpty()) {
        final List<RequestEntry<Integer>> requestEntries = new LinkedList<>();

        while ((requestEntries.size() < 10) && Objects.nonNull(ringBlockingQueue.peek())) {
          try {
            requestEntries.add(ringBlockingQueue.take());
          } catch (final InterruptedException e) {
            e.printStackTrace(System.err);
            Thread.currentThread().interrupt();
          }
        }

        requestEntriesOut.addAll(requestEntries);
      }
    }, 0, 100L, TimeUnit.MILLISECONDS);

    await().pollInterval(1, TimeUnit.SECONDS).pollDelay(200, TimeUnit.MILLISECONDS).until(() -> {
      return ringBlockingQueue.isEmpty();
    });

    producer.shutdownNow();
    consumer.shutdownNow();

    assertThat(ringBlockingQueue.isEmpty(), is(true));

    assertThat(requestEntriesOut, hasSize(100_000));
    requestEntriesOut.sort((a, b) -> a.getValue() - b.getValue());

    for (int i = 0; i < 100_000; i++) {
      assertThat(requestEntriesOut.get(i).getValue(), is(i));
    }
  }

  @Test
  void testConsumerReceivesElementsWhenQueueStartsEmpty() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();

    final ExecutorService producer = Executors.newSingleThreadExecutor();

    final ExecutorService consumer = Executors.newSingleThreadExecutor();

    consumer.submit(() -> {
      try {
        assertThat(ringBlockingQueue.take().getValue(), is(0));
        assertThat(ringBlockingQueue.take().getValue(), is(1));
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    await().pollDelay(2000, TimeUnit.MILLISECONDS).until(() -> true);

    producer.submit(() -> {
      try {
        ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(0).build());
        ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(1).build());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.writeIndex() == 2);
    producer.shutdownNow();

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.readIndex() == 2);
    consumer.shutdownNow();

    assertThat(ringBlockingQueue.isEmpty(), is(true));
  }

  @Test
  void testProducerResumesWhenQueueStartsFull() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>(1);

    final ExecutorService producer = Executors.newSingleThreadExecutor();

    final ExecutorService consumer = Executors.newSingleThreadExecutor();

    producer.submit(() -> {
      try {
        ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(0).build());
        ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(1).build());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    await().pollDelay(2000, TimeUnit.MILLISECONDS).until(() -> true);

    consumer.submit(() -> {
      try {
        assertThat(ringBlockingQueue.take().getValue(), is(0));
        assertThat(ringBlockingQueue.take().getValue(), is(1));
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.writeIndex() == 0);
    producer.shutdownNow();

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.readIndex() == 0);
    consumer.shutdownNow();

    assertThat(ringBlockingQueue.isEmpty(), is(true));
  }

  @Test
  void testCapacityIsRoundedUpToNextPowerOfTwo() {
    assertEquals(16, new RingBufferBlockingQueue<>(10).capacity());
    assertEquals(16, new RingBufferBlockingQueue<>(16).capacity());
    assertEquals(1, new RingBufferBlockingQueue<>(1).capacity());
    assertEquals(2048, new RingBufferBlockingQueue<>().capacity());
  }

  @Test
  void testUnsupportedOperationsThrow() {
    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(4);
    assertThrows(UnsupportedOperationException.class, () -> queue.offer(1));
    assertThrows(UnsupportedOperationException.class, () -> queue.offer(1, 1, TimeUnit.SECONDS));
    assertThrows(UnsupportedOperationException.class, queue::poll);
    assertThrows(UnsupportedOperationException.class, () -> queue.poll(1, TimeUnit.SECONDS));
    assertThrows(UnsupportedOperationException.class, queue::iterator);
    assertThrows(UnsupportedOperationException.class, () -> queue.drainTo(new ArrayList<>()));
    assertThrows(UnsupportedOperationException.class, () -> queue.drainTo(new ArrayList<>(), 1));
  }

  @Test
  void testSingleThreadFifoOrder() throws InterruptedException {
    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(16);
    for (int i = 0; i < 10; i++) {
      queue.put(i);
    }
    for (int i = 0; i < 10; i++) {
      assertEquals(Integer.valueOf(i), queue.take());
    }
    assertTrue(queue.isEmpty());
  }

  @Test
  void testIsFullIsEmptyAndRemainingCapacityTransitions() throws InterruptedException {
    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(4);
    assertTrue(queue.isEmpty());
    assertEquals(4, queue.remainingCapacity());

    for (int i = 0; i < 4; i++) {
      queue.put(i);
    }
    assertTrue(queue.isFull());
    assertEquals(0, queue.remainingCapacity());

    assertEquals(Integer.valueOf(0), queue.take());
    assertFalse(queue.isFull());
    assertEquals(1, queue.remainingCapacity());
  }

  @Test
  void testIndicesWrapAroundCorrectly() throws InterruptedException {
    final RingBufferBlockingQueue<Integer> queue = new RingBufferBlockingQueue<>(4);
    for (int cycle = 0; cycle < 100; cycle++) {
      queue.put(cycle);
      assertEquals(Integer.valueOf(cycle), queue.take());
    }
    assertTrue(queue.isEmpty());
    assertEquals(0, queue.size());
  }

  @Test
  void testPeekReturnsHeadWithoutRemoving() throws InterruptedException {
    final RingBufferBlockingQueue<String> queue = new RingBufferBlockingQueue<>(4);
    assertNull(queue.peek());

    queue.put("a");
    queue.put("b");

    assertEquals("a", queue.peek());
    assertEquals("a", queue.peek());
    assertEquals(2, queue.size());
    assertEquals("a", queue.take());
    assertEquals("b", queue.peek());
  }

}