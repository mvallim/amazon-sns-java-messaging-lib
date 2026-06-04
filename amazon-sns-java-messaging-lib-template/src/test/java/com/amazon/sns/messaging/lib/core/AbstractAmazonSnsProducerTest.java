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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

// @formatter:off
@ExtendWith(MockitoExtension.class)
class AbstractAmazonSnsProducerTest {

  @Mock
  private BlockingQueue<RequestEntry<String>> topicRequests;

  private ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests;

  private AbstractAmazonSnsProducer<String> producer;

  @BeforeEach
  void setUp() {
    pendingRequests = new ConcurrentHashMap<>();
    producer = new AbstractAmazonSnsProducer<String>(pendingRequests, topicRequests) { };
  }

  @AfterEach
  void tearDown() {
    if (Objects.nonNull(producer)) {
      producer.shutdown();
    }
  }

  @Test
  void testSendReturnsShutdownState() throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    final RequestEntry<String> entry = requestEntry();

    producer.shutdown();

    final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = producer.send(entry);

    assertThat(future, is(notNullValue()));

    future.addCallback(null, fail -> {
      assertThat(fail.getId(), is(entry.getId()));
      assertThat(fail.getCode(), is("000"));
      assertThat(fail.getMessage(), is("Producer is currently in SHUTDOWN mode; no further messages will be accepted."));
      assertThat(fail.getSenderFault(), is(true));
      countDownLatch.countDown();
    });

    countDownLatch.await(1, TimeUnit.MINUTES);
  }

  @Test
  void testSendReturnsNonNullFuture() {
    final RequestEntry<String> entry = requestEntry();

    final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = producer.send(entry);

    assertThat(future, is(notNullValue()));
  }

  @Test
  void testSendReturnsFutureOfCorrectType() {
    final RequestEntry<String> entry = requestEntry();

    final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = producer.send(entry);

    assertThat(future, instanceOf(ListenableFutureImpl.class));
  }

  @Test
  void testSendRegistersPendingRequest() {
    final RequestEntry<String> entry = requestEntry();

    producer.send(entry);

    assertThat(pendingRequests.containsKey(entry.getId()), is(true));
  }

  @Test
  void testSendEnqueuesEntryInTopicRequests() throws InterruptedException {
    final RequestEntry<String> entry = requestEntry();

    producer.send(entry);

    verify(topicRequests).put(entry);
  }

  @Test
  void testSendStoredFutureMatchesReturnedFuture() {
    final RequestEntry<String> entry = requestEntry();

    final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = producer.send(entry);

    assertThat(pendingRequests.get(entry.getId()), is(future));
  }

  @Test
  void testSendMultipleEntriesRegistersAllPendingRequests() {
    final RequestEntry<String> entry1 = requestEntry();
    final RequestEntry<String> entry2 = requestEntry();
    final RequestEntry<String> entry3 = requestEntry();

    producer.send(entry1);
    producer.send(entry2);
    producer.send(entry3);

    assertThat(pendingRequests.size(), is(3));
    assertThat(pendingRequests.containsKey(entry1.getId()), is(true));
    assertThat(pendingRequests.containsKey(entry2.getId()), is(true));
    assertThat(pendingRequests.containsKey(entry3.getId()), is(true));
  }

  @Test
  void testSendMultipleEntriesEnqueuesAllInTopicRequests() throws InterruptedException {
    final RequestEntry<String> entry1 = requestEntry();
    final RequestEntry<String> entry2 = requestEntry();

    producer.send(entry1);
    producer.send(entry2);

    verify(topicRequests).put(entry1);
    verify(topicRequests).put(entry2);
  }

  @Test
  void testSendPropagatesInterruptedExceptionFromQueue() throws InterruptedException {
    final RequestEntry<String> entry = requestEntry();
    doThrow(InterruptedException.class).when(topicRequests).put(any());

    assertThrows(InterruptedException.class, () -> producer.send(entry));
  }

  private RequestEntry<String> requestEntry() {
    return RequestEntry.<String>builder().withId(UUID.randomUUID().toString()).withValue("payload-" + UUID.randomUUID()).build();
  }

}
// @formatter:on