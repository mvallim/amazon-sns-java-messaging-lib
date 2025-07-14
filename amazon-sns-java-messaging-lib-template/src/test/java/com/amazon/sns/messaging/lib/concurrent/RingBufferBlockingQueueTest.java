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

package com.amazon.sns.messaging.lib.concurrent;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
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

@SuppressWarnings({ "java:S2925", "java:S5778" })
class RingBufferBlockingQueueTest {

  @Test
  void testSuccess() {
    final ExecutorService producer = Executors.newSingleThreadScheduledExecutor();

    final ScheduledExecutorService consumer = Executors.newSingleThreadScheduledExecutor();

    final List<RequestEntry<Integer>> requestEntriesOut = new LinkedList<>();

    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();

    producer.submit(() -> {
      IntStream.range(0, 100_000).forEach(value -> {
        ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(value).build());
        System.err.println("write: " + ringBlockingQueue.writeSequence());
      });
    });

    consumer.scheduleAtFixedRate(() -> {
      while (!ringBlockingQueue.isEmpty()) {
        final List<RequestEntry<Integer>> requestEntries = new LinkedList<>();

        while ((requestEntries.size() < 10) && Objects.nonNull(ringBlockingQueue.peek())) {
          final RequestEntry<Integer> take = ringBlockingQueue.take();
          System.err.println("read: " + ringBlockingQueue.readSequence());
          requestEntries.add(take);
        }

        requestEntriesOut.addAll(requestEntries);
      }
    }, 0, 100L, TimeUnit.MILLISECONDS);

    await().pollInterval(5, TimeUnit.SECONDS).pollDelay(200, TimeUnit.MILLISECONDS).until(() -> {
      return (ringBlockingQueue.writeSequence() == 99_999) && (ringBlockingQueue.readSequence() == 100_000);
    });

    producer.shutdown();
    consumer.shutdown();

    assertThat(ringBlockingQueue.size(), is(0));
    assertThat(ringBlockingQueue.isEmpty(), is(true));

    assertThat(requestEntriesOut, hasSize(100_000));
    requestEntriesOut.sort((a, b) -> a.getValue() - b.getValue());

    for (int i = 0; i < 100_000; i++) {
      assertThat(requestEntriesOut.get(i).getValue(), is(i));
    }
  }

  @Test
  void testSuccessWhenIsEmpty() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();

    final ExecutorService producer = Executors.newSingleThreadExecutor();

    final ExecutorService consumer = Executors.newSingleThreadExecutor();

    consumer.submit(() -> {
      assertThat(ringBlockingQueue.take().getValue(), is(0));
      assertThat(ringBlockingQueue.take().getValue(), is(1));
    });

    await().pollDelay(2, TimeUnit.SECONDS).until(() -> true);

    producer.submit(() -> {
      ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(0).build());
      ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(1).build());
    });

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.writeSequence() == 1);
    producer.shutdownNow();

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.readSequence() == 2);
    consumer.shutdownNow();

    assertThat(ringBlockingQueue.isEmpty(), is(true));
  }

  @Test
  void testSuccessWhenIsFull() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>(1);

    final ExecutorService producer = Executors.newSingleThreadExecutor();

    final ExecutorService consumer = Executors.newSingleThreadExecutor();

    producer.submit(() -> {
      ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(0).build());
      ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(1).build());
    });

    await().pollDelay(2, TimeUnit.SECONDS).until(() -> true);

    consumer.submit(() -> {
      assertThat(ringBlockingQueue.take().getValue(), is(0));
      assertThat(ringBlockingQueue.take().getValue(), is(1));
    });

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.writeSequence() == 1);
    producer.shutdownNow();

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.readSequence() == 2);
    consumer.shutdownNow();

    assertThat(ringBlockingQueue.isEmpty(), is(true));
  }

  @Test
  void testFailOffer() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();
    assertThrows(UnsupportedOperationException.class, () -> ringBlockingQueue.offer(RequestEntry.<Integer>builder().withValue(0).build()));
  }

  @Test
  void testFailOfferWithParams() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();
    assertThrows(UnsupportedOperationException.class, () -> ringBlockingQueue.offer(RequestEntry.<Integer>builder().withValue(0).build(), 1, TimeUnit.MILLISECONDS));
  }

  @Test
  void testFailPoll() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();
    assertThrows(UnsupportedOperationException.class, ringBlockingQueue::poll);
  }

  @Test
  void testFailPollWithParams() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();
    assertThrows(UnsupportedOperationException.class, () -> ringBlockingQueue.poll(1, TimeUnit.MILLISECONDS));
  }

  @Test
  void testFailIterator() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();
    assertThrows(UnsupportedOperationException.class, ringBlockingQueue::iterator);
  }

  @Test
  void testFailAdd() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();
    assertThrows(UnsupportedOperationException.class, () -> ringBlockingQueue.add(RequestEntry.<Integer>builder().withValue(0).build()));
  }

  @Test
  void testFailRemainingCapacity() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();
    assertThrows(UnsupportedOperationException.class, ringBlockingQueue::remainingCapacity);
  }

  @Test
  void testFailDrainTo() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();
    assertThrows(UnsupportedOperationException.class, () -> ringBlockingQueue.drainTo(Collections.emptyList()));
  }

  @Test
  void testFailDrainToWithParams() {
    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>();
    assertThrows(UnsupportedOperationException.class, () -> ringBlockingQueue.drainTo(Collections.emptyList(), 1));
  }

}
