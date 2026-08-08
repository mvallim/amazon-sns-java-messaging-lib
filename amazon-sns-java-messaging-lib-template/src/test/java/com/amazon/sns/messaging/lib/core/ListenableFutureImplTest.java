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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

// @formatter:off
class ListenableFutureImplTest {

  private static final Duration CALLBACK_TIMEOUT = Duration.ofSeconds(5);

  private ExecutorService callbackExecutor;

  @BeforeEach
  void setUp() {
    callbackExecutor = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  void tearDown() {
    callbackExecutor.shutdownNow();
  }

  @Test
  void testSuccessWithCallbacksBefore() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<ResponseSuccessEntry> captured = new AtomicReference<>();

    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> {
      captured.set(entry);
      latch.countDown();
    };
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> { throw new AssertionError("should not be called"); };

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.addCallback(successCallback, failureCallback);

    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);
    listenableFutureRegistry.success(entry);

    assertTrue(latch.await(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "success callback was not invoked in time");
    assertThat(captured.get(), sameInstance(entry));
  }

  @Test
  void testFailWithCallbacksBefore() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<ResponseFailEntry> captured = new AtomicReference<>();

    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> { throw new AssertionError("should not be called"); };
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> {
      captured.set(entry);
      latch.countDown();
    };

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.addCallback(successCallback, failureCallback);

    final ResponseFailEntry entry = mock(ResponseFailEntry.class);
    listenableFutureRegistry.fail(entry);

    assertTrue(latch.await(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "failure callback was not invoked in time");
    assertThat(captured.get(), sameInstance(entry));
  }

  @Test
  void testSuccessWithCallbacksAfter() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<ResponseSuccessEntry> captured = new AtomicReference<>();

    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> {
      captured.set(entry);
      latch.countDown();
    };
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> { throw new AssertionError("should not be called"); };

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);
    listenableFutureRegistry.success(entry);

    listenableFutureRegistry.addCallback(successCallback, failureCallback);

    assertTrue(latch.await(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "success callback was not invoked in time");
    assertThat(captured.get(), sameInstance(entry));
  }

  @Test
  void testFailWithCallbacksAfter() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<ResponseFailEntry> captured = new AtomicReference<>();

    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> { throw new AssertionError("should not be called"); };
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> {
      captured.set(entry);
      latch.countDown();
    };

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final ResponseFailEntry entry = mock(ResponseFailEntry.class);
    listenableFutureRegistry.fail(entry);

    listenableFutureRegistry.addCallback(successCallback, failureCallback);

    assertTrue(latch.await(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "failure callback was not invoked in time");
    assertThat(captured.get(), sameInstance(entry));
  }

  @Test
  void testSuccessWithCallbackSuccessBefore() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<ResponseSuccessEntry> captured = new AtomicReference<>();

    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> {
      captured.set(entry);
      latch.countDown();
    };

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.addCallback(successCallback, null);

    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);
    listenableFutureRegistry.success(entry);

    assertTrue(latch.await(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "success callback was not invoked in time");
    assertThat(captured.get(), sameInstance(entry));
  }

  @Test
  void testSuccessWithCallbackSuccessAfter() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<ResponseSuccessEntry> captured = new AtomicReference<>();

    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> {
      captured.set(entry);
      latch.countDown();
    };

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);
    listenableFutureRegistry.success(entry);

    listenableFutureRegistry.addCallback(successCallback, null);

    assertTrue(latch.await(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "success callback was not invoked in time");
    assertThat(captured.get(), sameInstance(entry));
  }

  @Test
  void testSuccessWithCallbackFailBefore() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<ResponseFailEntry> captured = new AtomicReference<>();

    final Consumer<? super ResponseFailEntry> failureCallback = entry -> {
      captured.set(entry);
      latch.countDown();
    };

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.addCallback(null, failureCallback);

    final ResponseFailEntry entry = mock(ResponseFailEntry.class);
    listenableFutureRegistry.fail(entry);

    assertTrue(latch.await(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "failure callback was not invoked in time");
    assertThat(captured.get(), sameInstance(entry));
  }

  @Test
  void testSuccessWithCallbackFailAfter() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<ResponseFailEntry> captured = new AtomicReference<>();

    final Consumer<? super ResponseFailEntry> failureCallback = entry -> {
      captured.set(entry);
      latch.countDown();
    };

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final ResponseFailEntry entry = mock(ResponseFailEntry.class);
    listenableFutureRegistry.fail(entry);

    listenableFutureRegistry.addCallback(null, failureCallback);

    assertTrue(latch.await(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "failure callback was not invoked in time");
    assertThat(captured.get(), sameInstance(entry));
  }

  @Test
  void testSuccessWithoutCallbacksBefore() throws Exception {
    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.addCallback(null, null);

    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);
    listenableFutureRegistry.success(entry);

    assertThat(listenableFutureRegistry.get(), sameInstance(entry));
  }

  @Test
  void testSuccessWithoutCallbacksAfter() {
    final RuntimeException cause = new RuntimeException("boom");
    final ResponseFailEntry entry = ResponseFailEntry.builder()
      .withId("id-1")
      .withMessage("boom")
      .withThrowable(cause)
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.fail(entry);

    listenableFutureRegistry.addCallback(null, null);

    final ExecutionException ex = assertThrows(ExecutionException.class, listenableFutureRegistry::get);
    assertThat(ex.getCause(), sameInstance(cause));
  }

  @Test
  void testEnsureNotCompletedThrowsWhenSuccessIsCalledTwice() throws Exception {
    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final ResponseSuccessEntry firstEntry = mock(ResponseSuccessEntry.class);

    listenableFutureRegistry.success(firstEntry);

    final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      listenableFutureRegistry.success(mock(ResponseSuccessEntry.class)));

    assertThat(ex.getMessage(), is("ListenableFuture already completed."));
    assertThat(listenableFutureRegistry.get(), sameInstance(firstEntry));
  }

  @Test
  void testEnsureNotCompletedThrowsWhenFailIsCalledAfterSuccess() throws Exception {
    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final ResponseSuccessEntry firstEntry = mock(ResponseSuccessEntry.class);

    listenableFutureRegistry.success(firstEntry);

    final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      listenableFutureRegistry.fail(mock(ResponseFailEntry.class)));

    assertThat(ex.getMessage(), is("ListenableFuture already completed."));
    assertThat(listenableFutureRegistry.get(), sameInstance(firstEntry));
  }

  @Test
  void testEnsureNotCompletedThrowsWhenFailIsCalledTwice() {
    final RuntimeException firstCause = new RuntimeException("first boom");
    final ResponseFailEntry firstEntry = ResponseFailEntry.builder()
      .withId("id-1")
      .withMessage("first boom")
      .withThrowable(firstCause)
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.fail(firstEntry);

    final RuntimeException secondCause = new RuntimeException("second boom");
    final ResponseFailEntry secondEntry = ResponseFailEntry.builder()
      .withId("id-1")
      .withMessage("second boom")
      .withThrowable(secondCause)
      .build();

    final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      listenableFutureRegistry.fail(secondEntry));

    assertThat(ex.getMessage(), is("ListenableFuture already completed."));

    final ExecutionException getEx = assertThrows(ExecutionException.class, listenableFutureRegistry::get);
    assertThat(getEx.getCause(), sameInstance(firstCause));
  }

  @Test
  void testEnsureNotCompletedThrowsWhenSuccessIsCalledAfterFail() {
    final RuntimeException firstCause = new RuntimeException("boom");
    final ResponseFailEntry firstEntry = ResponseFailEntry.builder()
      .withId("id-1")
      .withMessage("boom")
      .withThrowable(firstCause)
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.fail(firstEntry);

    final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      listenableFutureRegistry.success(mock(ResponseSuccessEntry.class)));

    assertThat(ex.getMessage(), is("ListenableFuture already completed."));

    final ExecutionException getEx = assertThrows(ExecutionException.class, listenableFutureRegistry::get);
    assertThat(getEx.getCause(), sameInstance(firstCause));
  }

  @Test
  void testGetReturnsImmediatelyWhenAlreadySucceeded() throws Exception {
    final ResponseSuccessEntry entry = ResponseSuccessEntry.builder()
      .withId("id-1")
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.success(entry);

    assertThat(listenableFutureRegistry.get(), sameInstance(entry));
  }

  @Test
  void testGetThrowsExecutionExceptionWithOriginalThrowableWhenAlreadyFailed() {
    final RuntimeException cause = new RuntimeException("boom");

    final ResponseFailEntry entry = ResponseFailEntry.builder()
      .withId("id-1")
      .withMessage("boom")
      .withThrowable(cause)
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.fail(entry);

    final ExecutionException ex = assertThrows(ExecutionException.class, listenableFutureRegistry::get);

    assertThat(ex.getCause(), sameInstance(cause));
  }

  @Test
  void testGetThrowsExecutionExceptionWithFallbackCauseWhenThrowableIsAbsent() {
    final ResponseFailEntry entry = ResponseFailEntry.builder()
      .withId("id-1")
      .withMessage("no throwable available")
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.fail(entry);

    final ExecutionException ex = assertThrows(ExecutionException.class, listenableFutureRegistry::get);

    assertThat(ex.getCause(), instanceOf(IllegalStateException.class));
    assertThat(ex.getCause().getMessage(), is("no throwable available"));
  }

  @Test
  void testGetBlocksUntilSuccessIsCalledFromAnotherThread() {
    final ResponseSuccessEntry entry = ResponseSuccessEntry.builder()
      .withId("id-1")
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final CountDownLatch aboutToBlock = new CountDownLatch(1);
    final AtomicReference<ResponseSuccessEntry> result = new AtomicReference<>();
    final AtomicReference<Throwable> error = new AtomicReference<>();

    final Thread waiter = new Thread(() -> {
      try {
        aboutToBlock.countDown();
        result.set(listenableFutureRegistry.get());
      } catch (final Exception ex) {
        error.set(ex);
      }
    });

    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      waiter.start();
      aboutToBlock.await();

      Thread.sleep(100);

      listenableFutureRegistry.success(entry);

      waiter.join();
    });

    assertThat(error.get(), nullValue());
    assertThat(result.get(), sameInstance(entry));
  }

  @Test
  void testGetBlocksUntilFailIsCalledFromAnotherThread() {
    final RuntimeException cause = new RuntimeException("boom");

    final ResponseFailEntry entry = ResponseFailEntry.builder()
      .withId("id-1")
      .withMessage("boom")
      .withThrowable(cause)
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final CountDownLatch aboutToBlock = new CountDownLatch(1);
    final AtomicReference<Throwable> error = new AtomicReference<>();

    final Thread waiter = new Thread(() -> {
      try {
        aboutToBlock.countDown();
        listenableFutureRegistry.get();
      } catch (final Exception ex) {
        error.set(ex);
      }
    });

    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      waiter.start();
      aboutToBlock.await();

      Thread.sleep(100);

      listenableFutureRegistry.fail(entry);

      waiter.join();
    });

    assertThat(error.get(), instanceOf(ExecutionException.class));
    assertThat(error.get().getCause(), sameInstance(cause));
  }

  @Test
  void testGetPropagatesInterruptedExceptionWhenInterruptedWhileWaiting() {
    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final CountDownLatch aboutToBlock = new CountDownLatch(1);
    final AtomicReference<Throwable> error = new AtomicReference<>();

    final Thread waiter = new Thread(() -> {
      try {
        aboutToBlock.countDown();
        listenableFutureRegistry.get();
      } catch (final Exception ex) {
        error.set(ex);
      }
    });

    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      waiter.start();
      aboutToBlock.await();

      Thread.sleep(100);

      waiter.interrupt();
      waiter.join();
    });

    assertThat(error.get(), instanceOf(InterruptedException.class));
  }

  @Test
  void testGetWithDurationReturnsImmediatelyWhenAlreadySucceeded() throws Exception {
    final ResponseSuccessEntry entry = ResponseSuccessEntry.builder()
      .withId("id-1")
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.success(entry);

    assertThat(listenableFutureRegistry.get(Duration.ofSeconds(1)), sameInstance(entry));
  }

  @Test
  void testGetWithDurationCompletesBeforeTimeoutElapses() {
    final ResponseSuccessEntry entry = ResponseSuccessEntry.builder()
      .withId("id-1")
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final AtomicReference<ResponseSuccessEntry> result = new AtomicReference<>();
    final AtomicReference<Throwable> error = new AtomicReference<>();

    final Thread waiter = new Thread(() -> {
      try {
        result.set(listenableFutureRegistry.get(Duration.ofSeconds(5)));
      } catch (final Exception ex) {
        error.set(ex);
      }
    });

    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      waiter.start();

      Thread.sleep(100);

      listenableFutureRegistry.success(entry);

      waiter.join();
    });

    assertThat(error.get(), nullValue());
    assertThat(result.get(), sameInstance(entry));
  }

  @Test
  void testGetWithDurationThrowsTimeoutExceptionWhenNeverCompleted() {
    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
      assertThrows(TimeoutException.class, () -> listenableFutureRegistry.get(Duration.ofMillis(100)))
    );

    assertDoesNotThrow(() -> listenableFutureRegistry.success(mock(ResponseSuccessEntry.class)));
  }

  @Test
  void testGetWithDurationThrowsNullPointerExceptionWhenTimeoutIsNull() {
    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    assertThrows(NullPointerException.class, () -> listenableFutureRegistry.get(null));
  }

  @Test
  void testGetWithDurationThrowsExecutionExceptionWhenAlreadyFailed() {
    final RuntimeException cause = new RuntimeException("boom");

    final ResponseFailEntry entry = ResponseFailEntry.builder()
      .withId("id-1")
      .withMessage("boom")
      .withThrowable(cause)
      .build();

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    listenableFutureRegistry.fail(entry);

    final ExecutionException ex = assertThrows(ExecutionException.class, () ->
      listenableFutureRegistry.get(Duration.ofSeconds(1)));

    assertThat(ex.getCause(), sameInstance(cause));
  }

  @Test
  void testGetWithDurationTimesOutSeparatelyFromInterruption() {
    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl(callbackExecutor);

    final AtomicReference<Throwable> error = new AtomicReference<>();
    final CountDownLatch aboutToBlock = new CountDownLatch(1);

    final Thread waiter = new Thread(() -> {
      try {
        aboutToBlock.countDown();
        listenableFutureRegistry.get(Duration.ofMillis(200));
      } catch (final Exception ex) {
        error.set(ex);
      }
    });

    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      waiter.start();
      aboutToBlock.await();
      waiter.join();
    });

    assertThat(error.get(), instanceOf(TimeoutException.class));
  }

}
// @formatter:on