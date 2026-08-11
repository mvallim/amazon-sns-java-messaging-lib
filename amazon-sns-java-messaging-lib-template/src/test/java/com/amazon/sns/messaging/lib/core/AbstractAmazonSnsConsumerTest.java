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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazon.sns.messaging.lib.concurrent.RingBufferBlockingQueue;
import com.amazon.sns.messaging.lib.core.RequestEntryInternalFactory.RequestEntryInternal;
import com.amazon.sns.messaging.lib.exception.PoisonRequestEntryException;
import com.amazon.sns.messaging.lib.helpers.TryConsumer;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

// @formatter:off
@ExtendWith(MockitoExtension.class)
class AbstractAmazonSnsConsumerTest {

  private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:test-topic";
  private static final long LINGER_MS = 50L;
  private static final int MAX_BATCH_SIZE = 10;

  @Mock(strictness = Strictness.LENIENT)
  private Object amazonSnsClient;

  @Mock(strictness = Strictness.LENIENT)
  private TopicProperty topicProperty;

  @Mock(strictness = Strictness.LENIENT)
  private ExecutorService executorService;

  @Mock(strictness = Strictness.LENIENT)
  private ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> listenableFutureImpl;

  private ObjectMapper objectMapper;

  private ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests;

  private BlockingQueue<RequestEntry<String>> topicRequests;

  private UnaryOperator<Object> publishDecorator;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    pendingRequests = new ConcurrentHashMap<>();
    topicRequests = new RingBufferBlockingQueue<>();
    publishDecorator = UnaryOperator.identity();

    when(topicProperty.getTopicArn()).thenReturn(TOPIC_ARN);
    when(topicProperty.getLinger()).thenReturn(LINGER_MS);
    when(topicProperty.getMaxBatchSize()).thenReturn(MAX_BATCH_SIZE);
    when(topicProperty.isFifo()).thenReturn(false);
  }

  @Nested
  class Constructor {

    @Test
    void testConstructorInitializesPendingRequests() {
      assertThat(pendingRequests, is(notNullValue()));
      assertThat(pendingRequests.isEmpty(), is(true));
    }

    @Test
    void testConstructorInitializesTopicRequests() {
      assertThat(topicRequests, is(notNullValue()));
      assertThat(topicRequests.isEmpty(), is(true));
    }

    @Test
    void testConstructorThrowsNpeWhenTopicPropertyIsNull() {
      final NullPointerException thrown = assertThrows(NullPointerException.class, () ->
        new TestableAmazonSnsConsumer(amazonSnsClient, null, objectMapper, pendingRequests, topicRequests, executorService, publishDecorator)
      );

      assertThat(thrown.getMessage(), containsString("topicProperty cannot be null"));
    }

    @Test
    void testConstructorThrowsNpeWhenAmazonSnsClientIsNull() {
      final NullPointerException thrown = assertThrows(NullPointerException.class, () ->
        new TestableAmazonSnsConsumer(null, topicProperty, objectMapper, pendingRequests, topicRequests, executorService, publishDecorator)
      );

      assertThat(thrown.getMessage(), containsString("amazonSnsClient cannot be null"));
    }

    @Test
    void testConstructorThrowsNpeWhenObjectMapperIsNull() {
      final NullPointerException thrown = assertThrows(NullPointerException.class, () ->
        new TestableAmazonSnsConsumer(amazonSnsClient, topicProperty, null, pendingRequests, topicRequests, executorService, publishDecorator)
      );

      assertThat(thrown.getMessage(), containsString("objectMapper cannot be null"));
    }

    @Test
    void testConstructorThrowsNpeWhenExecutorServiceIsNull() {
      final NullPointerException thrown = assertThrows(NullPointerException.class, () ->
        new TestableAmazonSnsConsumer(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, null, publishDecorator)
      );

      assertThat(thrown.getMessage(), containsString("executorService cannot be null"));
    }
  }

  @Nested
  class Await {

    @Test
    void testAwaitReturnCompletableFutureWhenQueuesAreEmpty() throws Exception {
      context(consumer -> {
        final CompletableFuture<Void> future = consumer.await();
        assertThat(future, is(notNullValue()));
        future.get(2, TimeUnit.SECONDS);
        assertThat(future.isDone(), is(true));
      });
    }

    @Test
    void testAwaitReturnNonNullFuture() throws Exception {
      context(consumer -> {
        final CompletableFuture<Void> future = consumer.await();
        assertThat(future, is(notNullValue()));
      });
    }

    @Test
    void testAwaitCompletesWhenPendingRequestsAndTopicRequestsAreEmpty() throws Exception {
      context(consumer -> {
        final CompletableFuture<Void> future = consumer.await();
        future.get(2, TimeUnit.SECONDS);

        assertThat(future.isDone(), is(true));
        assertThat(future.isCompletedExceptionally(), is(false));
      });
    }

    @Test
    void testAwaitEventuallyCompletesAfterPendingRequestsAreCleared() throws Exception {
      pendingRequests.put("key-1", listenableFutureImpl);

      context(consumer -> {
        final CompletableFuture<Void> future = consumer.await();

        assertThat(future.isDone(), is(false));

        pendingRequests.clear();

        future.get(3, TimeUnit.SECONDS);
        assertThat(future.isDone(), is(true));
      });
    }
  }

  @Nested
  class AwaitWithTimeout {

    @Test
    void testAwaitWithTimeoutThrowsNpeWhenTimeoutIsNull() throws Exception {
      context(consumer ->
        assertThrows(NullPointerException.class, () -> consumer.await(null))
      );
    }

    @Test
    void testAwaitWithTimeoutReturnsNonNullFuture() throws Exception {
      context(consumer -> {
        final CompletableFuture<Void> future = consumer.await(Duration.ofSeconds(2));
        assertThat(future, is(notNullValue()));
      });
    }

    @Test
    void testAwaitWithTimeoutCompletesWhenQueuesAreEmpty() throws Exception {
      context(consumer -> {
        final CompletableFuture<Void> future = consumer.await(Duration.ofSeconds(2));
        future.get(3, TimeUnit.SECONDS);

        assertThat(future.isDone(), is(true));
        assertThat(future.isCompletedExceptionally(), is(false));
      });
    }

    @Test
    void testAwaitWithTimeoutEventuallyCompletesAfterPendingRequestsAreCleared() throws Exception {
      pendingRequests.put("key-1", listenableFutureImpl);

      context(consumer -> {
        final CompletableFuture<Void> future = consumer.await(Duration.ofSeconds(5));

        assertThat(future.isDone(), is(false));

        pendingRequests.clear();

        future.get(6, TimeUnit.SECONDS);
        assertThat(future.isDone(), is(true));
        assertThat(future.isCompletedExceptionally(), is(false));
      });
    }

    @Test
    void testAwaitWithTimeoutCompletesExceptionallyWhenTimeoutElapses() throws Exception {
      pendingRequests.put("key-1", listenableFutureImpl);

      context(consumer -> {
        final CompletableFuture<Void> future = consumer.await(Duration.ofMillis(100));

        try {
          final ExecutionException thrown = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));

          assertThat(thrown.getCause(), instanceOf(TimeoutException.class));
        } finally {
          pendingRequests.clear();
        }
      });
    }

    @Test
    void testAwaitWithTimeoutStillCompletesEvenThoughUnderlyingDrainKeepsRunning() throws Exception {
      pendingRequests.put("key-1", listenableFutureImpl);

      context(consumer -> {
        final CompletableFuture<Void> future = consumer.await(Duration.ofMillis(100));

        try {
          assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
          assertThat(future.isCompletedExceptionally(), is(true));
        } finally {
          pendingRequests.clear();
        }
      });
    }
  }

  @Nested
  class Shutdown {

    @Test
    void testShutdownExecutorService() throws InterruptedException {
      try (final MockedStatic<Executors> mockedStatic = mockStatic(Executors.class)) {

        final ExecutorService executorService = mock();
        final ScheduledExecutorService scheduledExecutorService = mock();

        mockedStatic.when(() -> Executors.newSingleThreadScheduledExecutor(any())).thenReturn(scheduledExecutorService);

        try (final TestableAmazonSnsConsumer snsConsumer = new TestableAmazonSnsConsumer(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, executorService, publishDecorator)) {
          when(executorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(false);
          when(executorService.shutdownNow()).thenReturn(Collections.singletonList(mock()));
          when(scheduledExecutorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);

          snsConsumer.shutdown();

          verify(executorService).shutdown();
          verify(executorService).awaitTermination(60, TimeUnit.SECONDS);
          verify(executorService).shutdownNow();
          verify(scheduledExecutorService).shutdown();
          verify(scheduledExecutorService).awaitTermination(60, TimeUnit.SECONDS);
          verify(scheduledExecutorService, never()).shutdownNow();
        }
      }
    }

    @Test
    void testShutdownScheduledExecutorService() throws InterruptedException {
      try (final MockedStatic<Executors> mockedStatic = mockStatic(Executors.class)) {

        final ExecutorService executorService = mock();
        final ScheduledExecutorService scheduledExecutorService = mock();

        mockedStatic.when(() -> Executors.newSingleThreadScheduledExecutor(any())).thenReturn(scheduledExecutorService);

        try (final TestableAmazonSnsConsumer snsConsumer = new TestableAmazonSnsConsumer(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, executorService, publishDecorator)) {
          when(scheduledExecutorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(false);
          when(scheduledExecutorService.shutdownNow()).thenReturn(Collections.singletonList(mock()));
          when(executorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);

          snsConsumer.shutdown();

          verify(executorService).shutdown();
          verify(executorService).awaitTermination(60, TimeUnit.SECONDS);
          verify(executorService, never()).shutdownNow();
          verify(scheduledExecutorService).shutdown();
          verify(scheduledExecutorService).awaitTermination(60, TimeUnit.SECONDS);
          verify(scheduledExecutorService).shutdownNow();
        }
      }
    }

    @Test
    void testShutdownRiseInterruptedException() throws InterruptedException {
      try (final MockedStatic<Executors> mockedStatic = mockStatic(Executors.class)) {

        final ExecutorService executorService = mock();
        final ScheduledExecutorService scheduledExecutorService = mock();

        mockedStatic.when(() -> Executors.newSingleThreadScheduledExecutor(any())).thenReturn(scheduledExecutorService);

        try (final TestableAmazonSnsConsumer snsConsumer = new TestableAmazonSnsConsumer(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, executorService, publishDecorator)) {
          doAnswer(invocation -> {
            throw new InterruptedException("interrupt");
          }).when(executorService).shutdown();

          snsConsumer.shutdown();

          verify(executorService).shutdown();
          verify(executorService, never()).awaitTermination(60, TimeUnit.SECONDS);
          verify(executorService, never()).shutdownNow();
          verify(scheduledExecutorService).shutdown();
          verify(scheduledExecutorService).awaitTermination(60, TimeUnit.SECONDS);
          verify(scheduledExecutorService).shutdownNow();
        }
      }
    }

  }

  @Nested
  class Run {

    @Test
    void testRunWithFifoTopicPublishesSynchronously() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);
      when(executorService.submit(any(Runnable.class))).thenReturn(null);

      context(consumer -> {
        final RequestEntry<String> entry = buildRequestEntry("fifo-message");
        topicRequests.put(entry);

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getPublishCallCount(), greaterThanOrEqualTo(1));
            assertThat(consumer.getHandleResponseCallCount(), greaterThanOrEqualTo(1));
          });
      });
    }

    @Test
    void testRunWithNonFifoTopicPublishesAsynchronously() throws Exception {
      when(topicProperty.isFifo()).thenReturn(false);
      doAnswer(inv -> {
        ((Runnable) inv.getArgument(0)).run();
        return CompletableFuture.completedFuture(null);
      }).when(executorService).execute(any(Runnable.class));

      context(consumer -> {
        final RequestEntry<String> entry = buildRequestEntry("non-fifo-message");
        topicRequests.put(entry);

        await()
          .untilAsserted(() ->
            verify(executorService, atLeastOnce()).execute(any(Runnable.class))
          );
      });
    }

    @Test
    void testRunHandlesPublishExceptionWithoutCrashing() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);

      context(consumer -> {
        consumer.setThrowOnPublish(true);

        final RequestEntry<String> entry = buildRequestEntry("error-message");
        topicRequests.put(entry);

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getHandleErrorCallCount(), greaterThanOrEqualTo(1));
            assertThat(consumer.getLastError(), is(notNullValue()));
          });
      });
    }

    @Test
    void testRunRecordsCorrectExceptionOnPublishFailure() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);

      context(consumer -> {
        consumer.setThrowOnPublish(true);

        topicRequests.put(buildRequestEntry("fail-message"));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getLastError(), instanceOf(RuntimeException.class));
            assertThat(consumer.getLastError().getMessage(), containsString("publish failed"));
          });
      });
    }

    @Test
    void testRunHandlesRejectedExecutionExceptionFromExecutorWithoutCrashing() throws Exception {
      when(topicProperty.isFifo()).thenReturn(false);
      doThrow(new RejectedExecutionException("executor full")).when(executorService).execute(any(Runnable.class));

      context(consumer -> {
        topicRequests.put(buildRequestEntry("rejected-message"));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getHandleErrorCallCount(), greaterThanOrEqualTo(1));
            assertThat(consumer.getLastError(), instanceOf(RejectedExecutionException.class));
          });
      });
    }

    @Test
    void testRunDoesNotPublishWhenQueueIsEmpty() throws Exception {
      context(consumer -> {
        await()
          .untilAsserted(() -> {
            assertThat(consumer.getPublishCallCount(), is(0));
            assertThat(consumer.getHandleErrorCallCount(), is(0));
          });
      });
    }

    @Test
    void testRunPublishesMultipleEntriesInSingleBatch() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);

      context(consumer -> {
        for (int i = 0; i < 5; i++) {
          topicRequests.put(buildRequestEntry("message-" + i));
        }

        await()
          .untilAsserted(() ->
            assertThat(consumer.getPublishCallCount(), greaterThanOrEqualTo(1))
          );
      });
    }

    @Test
    void testRunRespectMaxBatchSizeByPublishingInMultipleBatches() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);
      when(topicProperty.getMaxBatchSize()).thenReturn(2);

      context(consumer -> {
        for (int i = 0; i < 6; i++) {
          topicRequests.put(buildRequestEntry("msg-" + i));
        }

        await()
          .untilAsserted(() ->
            assertThat(consumer.getPublishCallCount(), greaterThanOrEqualTo(2))
          );
      });
    }
  }

  @Nested
  class PendingRequests {

    @Test
    void testPendingRequestsIsEmptyOnConstruction() {
      assertThat(pendingRequests.isEmpty(), is(true));
    }

    @Test
    void testPendingRequestsCanHoldMultipleEntries() {
      pendingRequests.put("id-1", listenableFutureImpl);
      pendingRequests.put("id-2", listenableFutureImpl);

      assertThat(pendingRequests.size(), is(2));
      assertThat(pendingRequests, hasKey("id-1"));
      assertThat(pendingRequests, hasKey("id-2"));
    }

    @Test
    void testPendingRequestsCanBeRemovedAfterProcessing() {
      pendingRequests.put("id-1", listenableFutureImpl);
      pendingRequests.remove("id-1");

      assertThat(pendingRequests.isEmpty(), is(true));
    }
  }

  @Nested
  class TopicRequests {

    @Test
    void testTopicRequestsIsEmptyOnConstruction() {
      assertThat(topicRequests.isEmpty(), is(true));
    }

    @Test
    void testTopicRequestsAcceptsRequestEntries() throws InterruptedException {
      topicRequests.put(buildRequestEntry("payload-1"));
      topicRequests.put(buildRequestEntry("payload-2"));

      assertThat(topicRequests.size(), is(2));
    }

    @Test
    void testTopicRequestsPollRemovesEntry() throws InterruptedException {
      topicRequests.put(buildRequestEntry("payload"));

      final RequestEntry<String> polled = topicRequests.take();

      assertThat(polled, is(notNullValue()));
      assertThat(topicRequests.isEmpty(), is(true));
    }
  }

  @Nested
  class PublishDecorator {

    @Test
    void testPublishDecoratorIsAppliedBeforePublish() throws Exception {
      final Object decoratedObject = new Object();
      final UnaryOperator<Object> trackingDecorator = req -> decoratedObject;

      when(topicProperty.isFifo()).thenReturn(true);

      context(trackingDecorator, consumer -> {
        topicRequests.put(buildRequestEntry("decorated-message"));

        await()
          .untilAsserted(() ->
            assertThat(consumer.getPublishCallCount(), greaterThanOrEqualTo(1))
          );
      });
    }

    @Test
    void testPublishDecoratorIdentityDoesNotAlterRequest() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);

      context(consumer -> {
        topicRequests.put(buildRequestEntry("identity-message"));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getPublishCallCount(), greaterThanOrEqualTo(1));
            assertThat(consumer.getHandleErrorCallCount(), is(0));
          });
      });
    }
  }

  @Nested
  class CanAddPayload {

    @Test
    void testCanAddPayloadAllowsEntryWellBelowSizeThreshold() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);

      context(consumer -> {
        topicRequests.put(buildRequestEntry("small-payload"));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getTotalPublishedEntries(), is(1));
            assertThat(consumer.getHandleErrorCallCount(), is(0));
          });
      });
    }

    @Test
    void testCanAddPayloadAllowsEntryExactlyAtSizeThreshold() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);

      context(consumer -> {
        final String payloadAtThreshold = buildPayloadOfBytes(TestableAmazonSnsConsumer.batchSizeBytesThreshold());
        topicRequests.put(buildRequestEntry(payloadAtThreshold));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getTotalPublishedEntries(), is(1));
            assertThat(consumer.getHandleErrorCallCount(), is(0));
          });
      });
    }

    @Test
    void testCanAddPayloadRejectsEntryExceedingSizeThreshold() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);

      context(consumer -> {
        final String oversizedPayload = buildPayloadOfBytes(TestableAmazonSnsConsumer.batchSizeBytesThreshold() + 1);
        topicRequests.put(buildRequestEntry(oversizedPayload));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getTotalPublishedEntries(), is(0));
            assertThat(consumer.getHandleErrorCallCount(), greaterThanOrEqualTo(0));
          });
      });
    }

    @Test
    void testCanAddPayloadStopsAccumulatingWhenBatchExceedsThreshold() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);
      when(topicProperty.getMaxBatchSize()).thenReturn(10);

      context(consumer -> {
        final int halfThreshold = TestableAmazonSnsConsumer.batchSizeBytesThreshold() / 2;
        topicRequests.put(buildRequestEntry(buildPayloadOfBytes(halfThreshold)));
        topicRequests.put(buildRequestEntry(buildPayloadOfBytes(halfThreshold)));
        topicRequests.put(buildRequestEntry("small-overflow"));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getPublishCallCount(), greaterThanOrEqualTo(2));
            assertThat(consumer.getTotalPublishedEntries(), is(3));
          });
      });
    }

    @Test
    void testCanAddPayloadPublishesFirstEntryAloneWhenItFillsThreshold() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);
      when(topicProperty.getMaxBatchSize()).thenReturn(10);

      context(consumer -> {
        final int fullThreshold = TestableAmazonSnsConsumer.batchSizeBytesThreshold();
        topicRequests.put(buildRequestEntry(buildPayloadOfBytes(fullThreshold)));
        topicRequests.put(buildRequestEntry("second-entry"));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getPublishCallCount(), greaterThanOrEqualTo(2));
            assertThat(consumer.getPublishedBatchSizes().get(0), is(1));
          });
      });
    }

    @Test
    void testCanAddPayloadAllowsMultipleSmallEntriesUpToThreshold() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);
      when(topicProperty.getMaxBatchSize()).thenReturn(100);

      context(consumer -> {
        for (int i = 0; i < 10; i++) {
          topicRequests.put(buildRequestEntry("entry-" + i));
        }

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getTotalPublishedEntries(), is(10));
            assertThat(consumer.getHandleErrorCallCount(), is(0));
          });
      });
    }

    @Test
    void testCanAddPayloadSplitsBatchWhenCumulativeSizeExceedsThreshold() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);
      when(topicProperty.getMaxBatchSize()).thenReturn(10);

      context(consumer -> {
        final int chunkSize = (TestableAmazonSnsConsumer.batchSizeBytesThreshold() / 3) + 1;
        topicRequests.put(buildRequestEntry(buildPayloadOfBytes(chunkSize)));
        topicRequests.put(buildRequestEntry(buildPayloadOfBytes(chunkSize)));
        topicRequests.put(buildRequestEntry(buildPayloadOfBytes(chunkSize)));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getPublishCallCount(), greaterThanOrEqualTo(2));
            assertThat(consumer.getTotalPublishedEntries(), is(3));
          });
      });
    }

    @Test
    void testCanAddPayloadDoesNotPublishEmptyBatchWhenAllEntriesExceedThreshold() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);

      context(consumer -> {
        final String oversizedPayload = buildPayloadOfBytes(TestableAmazonSnsConsumer.batchSizeBytesThreshold() + 100);
        topicRequests.put(buildRequestEntry(oversizedPayload));

        await()
          .untilAsserted(() -> {
            assertThat(consumer.getTotalPublishedEntries(), is(0));
            assertThat(consumer.getHandleErrorCallCount(), greaterThanOrEqualTo(0));
          });
      });
    }
  }

  @Nested
  class PoisonRequestEntry {

    @Test
    void testPoisonRequestEntryRemovesFromPendingRequestsAndFailsListenableFuture() throws Exception {
      when(topicProperty.isFifo()).thenReturn(true);

      final String poisonId = "poison-id";
      final String oversizedPayload = buildPayloadOfBytes(TestableAmazonSnsConsumer.batchSizeBytesThreshold() + 100);
      final RequestEntry<String> poisonEntry = RequestEntry.<String>builder()
        .withId(poisonId)
        .withValue(oversizedPayload)
        .build();

      pendingRequests.put(poisonId, listenableFutureImpl);

      context(consumer -> {
        topicRequests.put(poisonEntry);

        await()
          .untilAsserted(() -> {
            assertThat(pendingRequests.containsKey(poisonId), is(false));

            final ArgumentCaptor<ResponseFailEntry> captor = ArgumentCaptor.forClass(ResponseFailEntry.class);
            verify(listenableFutureImpl, atLeastOnce()).fail(captor.capture());

            final ResponseFailEntry failEntry = captor.getValue();
            assertThat(failEntry.getId(), is(poisonId));
            assertThat(failEntry.getCode(), is("000"));
            assertThat(failEntry.getSenderFault(), is(true));
            assertThat(failEntry.getThrowable(), instanceOf(PoisonRequestEntryException.class));
          });
      });
    }
  }

  private RequestEntry<String> buildRequestEntry(final String value) {
    return RequestEntry.<String>builder().withValue(value).build();
  }

  private String buildPayloadOfBytes(final int targetBytes) {
    return StringUtils.repeat('x', Math.max(0, targetBytes));
  }

  private void context(final TryConsumer<TestableAmazonSnsConsumer> consumer) throws Exception {
    try (final TestableAmazonSnsConsumer snsConsumer = new TestableAmazonSnsConsumer(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, executorService, publishDecorator)) {
      consumer.accept(snsConsumer);
    }
  }

  private void context(final UnaryOperator<Object> trackingDecorator, final TryConsumer<TestableAmazonSnsConsumer> consumer) throws Exception {
    try (final TestableAmazonSnsConsumer snsConsumer = new TestableAmazonSnsConsumer(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, executorService, trackingDecorator)) {
      consumer.accept(snsConsumer);
    }
  }

  static class TestableAmazonSnsConsumer extends AbstractAmazonSnsConsumer<Object, Object, Object, String> implements AutoCloseable {

    private static final int BATCH_SIZE_BYTES_THRESHOLD = 256 * 1024;

    private final AtomicInteger publishCallCount = new AtomicInteger(0);
    private final AtomicInteger handleErrorCallCount = new AtomicInteger(0);
    private final AtomicInteger handleResponseCallCount = new AtomicInteger(0);
    private Throwable lastError;
    private boolean throwOnPublish = false;
    private final RuntimeException publishException = new RuntimeException("publish failed");
    private final List<Integer> publishedBatchSizes = Collections.synchronizedList(new LinkedList<>());

    TestableAmazonSnsConsumer(
        final Object amazonSnsClient,
        final TopicProperty topicProperty,
        final ObjectMapper objectMapper,
        final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> pendingRequests,
        final BlockingQueue<RequestEntry<String>> topicRequests,
        final ExecutorService executorService,
        final UnaryOperator<Object> publishDecorator) {
      super(amazonSnsClient, topicProperty, objectMapper, pendingRequests, topicRequests, executorService, publishDecorator);
    }

    @Override
    public Object publish(final Object publishBatchRequest) {
      publishCallCount.incrementAndGet();
      if (throwOnPublish) {
        throw publishException;
      }
      return new Object();
    }

    @Override
    public void handleError(final Object publishBatchRequest, final Throwable throwable) {
      handleErrorCallCount.incrementAndGet();
      lastError = throwable;
    }

    @Override
    public void handleResponse(final Object publishBatchResult) {
      handleResponseCallCount.incrementAndGet();
    }

    @Override
    protected BiFunction<String, List<RequestEntryInternal>, Object> supplierPublishRequest() {
      return (topicArn, entries) -> {
        publishedBatchSizes.add(entries.size());
        return new Object();
      };
    }

    int getPublishCallCount() {
      return publishCallCount.get();
    }

    int getHandleErrorCallCount() {
      return handleErrorCallCount.get();
    }

    int getHandleResponseCallCount() {
      return handleResponseCallCount.get();
    }

    Throwable getLastError() {
      return lastError;
    }

    void setThrowOnPublish(final boolean throwOnPublish) {
      this.throwOnPublish = throwOnPublish;
    }

    List<Integer> getPublishedBatchSizes() {
      return publishedBatchSizes;
    }

    int getTotalPublishedEntries() {
      return publishedBatchSizes.stream().mapToInt(Integer::intValue).sum();
    }

    static int batchSizeBytesThreshold() {
      return BATCH_SIZE_BYTES_THRESHOLD;
    }

    @Override
    public void close() {
      shutdown();
    }

  }

}
//@formatter:on