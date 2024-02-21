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

class RingBufferBlockingQueueTest {

  private final ExecutorService producer = Executors.newSingleThreadExecutor();

  private final ScheduledExecutorService consumer = Executors.newSingleThreadScheduledExecutor();

  @Test
  void testSuccess() throws InterruptedException {
    final List<RequestEntry<Integer>> requestEntriesOut = new LinkedList<>();

    final RingBufferBlockingQueue<RequestEntry<Integer>> ringBlockingQueue = new RingBufferBlockingQueue<>(5120);

    producer.submit(() -> {
      IntStream.range(0, 100_000).forEach(value -> {
        ringBlockingQueue.put(RequestEntry.<Integer>builder().withValue(value).build());
      });
    });

    consumer.scheduleAtFixedRate(() -> {
      while (!ringBlockingQueue.isEmpty()) {
        final List<RequestEntry<Integer>> requestEntries = new LinkedList<>();

        while ((requestEntries.size() < 10) && Objects.nonNull(ringBlockingQueue.peek())) {
          requestEntries.add(ringBlockingQueue.take());
        }

        requestEntriesOut.addAll(requestEntries);
      }
    }, 0, 100L, TimeUnit.MILLISECONDS);

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.writeSequence() == 99_999);
    producer.shutdownNow();

    await().atMost(1, TimeUnit.MINUTES).until(() -> ringBlockingQueue.readSequence() == 100_000);
    consumer.shutdownNow();

    assertThat(ringBlockingQueue.isEmpty(), is(true));

    assertThat(requestEntriesOut, hasSize(100_000));
    requestEntriesOut.sort((a, b) -> a.getValue() - b.getValue());

    for (int i = 0; i < 100_000; i++) {
      assertThat(requestEntriesOut.get(i).getValue(), is(i));
    }
  }

}
