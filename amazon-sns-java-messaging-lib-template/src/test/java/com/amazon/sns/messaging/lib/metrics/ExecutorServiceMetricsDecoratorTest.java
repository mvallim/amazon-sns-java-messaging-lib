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

package com.amazon.sns.messaging.lib.metrics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class ExecutorServiceMetricsDecoratorTest {

  private ExecutorServiceMetricsDecorator decorator;

  @Mock
  private ExecutorService delegateMock;

  @Spy
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    decorator = new ExecutorServiceMetricsDecorator(delegateMock, meterRegistry, "test-executor");
  }

  @Test
  void testConstructorWithNullRegistryDoesNotThrow() {
    final ExecutorServiceMetricsDecorator instance = new ExecutorServiceMetricsDecorator(delegateMock, null, "test-executor");
    assertThat(instance, is(notNullValue()));
  }

  @Test
  void testConstructorWithValidRegistryCreatesInstance() {
    assertThat(decorator, is(notNullValue()));
  }

  @Test
  void testSubmitRunnableDelegatesToDelegate() {
    final Runnable task = mock(Runnable.class);
    final Future expectedFuture = mock(Future.class);
    when(delegateMock.submit(task)).thenReturn(expectedFuture);

    final Future<?> result = decorator.submit(task);

    assertThat(result, is(equalTo(expectedFuture)));
    verify(delegateMock).submit(task);
  }

  @Test
  void testSubmitRunnableWithResultDelegatesToDelegate() {
    final Runnable task = mock(Runnable.class);
    final String expectedResult = "result";
    final Future<String> expectedFuture = mock(Future.class);
    when(delegateMock.submit(task, expectedResult)).thenReturn(expectedFuture);

    final Future<String> result = decorator.submit(task, expectedResult);

    assertThat(result, is(equalTo(expectedFuture)));
    verify(delegateMock).submit(task, expectedResult);
  }

  @Test
  void testSubmitCallableDelegatesToDelegate() {
    final Callable<String> task = mock(Callable.class);
    final Future<String> expectedFuture = mock(Future.class);
    when(delegateMock.submit(task)).thenReturn(expectedFuture);

    final Future<String> result = decorator.submit(task);

    assertThat(result, is(equalTo(expectedFuture)));
    verify(delegateMock).submit(task);
  }

  @Test
  void testInvokeAnyDelegatesToDelegate() throws InterruptedException, ExecutionException {
    final Collection<Callable<String>> tasks = Collections.singletonList(mock(Callable.class));
    when(delegateMock.invokeAny(tasks)).thenReturn("done");

    final String result = decorator.invokeAny(tasks);

    assertThat(result, is(equalTo("done")));
    verify(delegateMock).invokeAny(tasks);
  }

  @Test
  void testInvokeAnyWithTimeoutDelegatesToDelegate() throws InterruptedException, ExecutionException, TimeoutException {
    final Collection<Callable<String>> tasks = Collections.singletonList(mock(Callable.class));
    when(delegateMock.invokeAny(tasks, 5L, TimeUnit.SECONDS)).thenReturn("done");

    final String result = decorator.invokeAny(tasks, 5L, TimeUnit.SECONDS);

    assertThat(result, is(equalTo("done")));
    verify(delegateMock).invokeAny(tasks, 5L, TimeUnit.SECONDS);
  }

  @Test
  void testInvokeAllDelegatesToDelegate() throws InterruptedException {
    final Collection<Callable<String>> tasks = Collections.singletonList(mock(Callable.class));
    final List<Future<String>> expectedFutures = Collections.singletonList(mock(Future.class));
    when(delegateMock.invokeAll(tasks)).thenReturn(expectedFutures);

    final List<Future<String>> result = decorator.invokeAll(tasks);

    assertThat(result, is(equalTo(expectedFutures)));
    verify(delegateMock).invokeAll(tasks);
  }

  @Test
  void testInvokeAllWithTimeoutDelegatesToDelegate() throws InterruptedException {
    final Collection<Callable<String>> tasks = Collections.singletonList(mock(Callable.class));
    final List<Future<String>> expectedFutures = Collections.singletonList(mock(Future.class));
    when(delegateMock.invokeAll(tasks, 5L, TimeUnit.SECONDS)).thenReturn(expectedFutures);

    final List<Future<String>> result = decorator.invokeAll(tasks, 5L, TimeUnit.SECONDS);

    assertThat(result, is(equalTo(expectedFutures)));
    verify(delegateMock).invokeAll(tasks, 5L, TimeUnit.SECONDS);
  }

  @Test
  void testShutdownDelegatesToDelegate() {
    decorator.shutdown();
    verify(delegateMock).shutdown();
  }

  @Test
  void testShutdownNowDelegatesToDelegate() {
    final List<Runnable> pending = Collections.singletonList(mock(Runnable.class));
    when(delegateMock.shutdownNow()).thenReturn(pending);

    final List<Runnable> result = decorator.shutdownNow();

    assertThat(result, is(equalTo(pending)));
    verify(delegateMock).shutdownNow();
  }

  @Test
  void testIsShutdownReturnsTrueWhenDelegateReturnsTrue() {
    when(delegateMock.isShutdown()).thenReturn(true);
    assertThat(decorator.isShutdown(), is(true));
  }

  @Test
  void testIsShutdownReturnsFalseWhenDelegateReturnsFalse() {
    when(delegateMock.isShutdown()).thenReturn(false);
    assertThat(decorator.isShutdown(), is(false));
  }

  @Test
  void testIsTerminatedReturnsTrueWhenDelegateReturnsTrue() {
    when(delegateMock.isTerminated()).thenReturn(true);
    assertThat(decorator.isTerminated(), is(true));
  }

  @Test
  void testIsTerminatedReturnsFalseWhenDelegateReturnsFalse() {
    when(delegateMock.isTerminated()).thenReturn(false);
    assertThat(decorator.isTerminated(), is(false));
  }

  @Test
  void testAwaitTerminationReturnsTrueWhenDelegateReturnsTrue() throws InterruptedException {
    when(delegateMock.awaitTermination(10L, TimeUnit.SECONDS)).thenReturn(true);
    assertThat(decorator.awaitTermination(10L, TimeUnit.SECONDS), is(true));
  }

  @Test
  void testAwaitTerminationReturnsFalseWhenDelegateReturnsFalse() throws InterruptedException {
    when(delegateMock.awaitTermination(10L, TimeUnit.SECONDS)).thenReturn(false);
    assertThat(decorator.awaitTermination(10L, TimeUnit.SECONDS), is(false));
  }

  @Test
  void testExecuteWrapsCommandAndDelegatesToDelegate() {
    final Runnable command = mock(Runnable.class);
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

    decorator.execute(command);

    verify(delegateMock).execute(captor.capture());
    assertThat(captor.getValue(), is(notNullValue()));
  }

  @Test
  void testExecuteWrappedRunnableRunsOriginalCommand() {
    final Runnable command = mock(Runnable.class);
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

    decorator.execute(command);
    verify(delegateMock).execute(captor.capture());

    captor.getValue().run();

    verify(command).run();
  }

  @Test
  void testExecuteIncrementsSucceededCounterOnSuccess() {
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

    decorator.execute(() -> {
    });
    verify(delegateMock).execute(captor.capture());
    captor.getValue().run();

    final double count = meterRegistry.counter("executor.tasks.succeeded", "name", "test-executor").count();
    assertThat(count, is(equalTo(1.0)));
  }

  @Test
  void testExecuteDoesNotIncrementFailedCounterOnSuccess() {
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

    decorator.execute(() -> {
    });
    verify(delegateMock).execute(captor.capture());
    captor.getValue().run();

    final double count = meterRegistry.counter("executor.tasks.failed", "name", "test-executor").count();
    assertThat(count, is(equalTo(0.0)));
  }

  @Test
  void testExecuteIncrementsFailedCounterOnException() {
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    final Runnable failingCommand = () -> {
      throw new RuntimeException("boom");
    };

    decorator.execute(failingCommand);
    verify(delegateMock).execute(captor.capture());

    try {
      captor.getValue().run();
    } catch (final RuntimeException ignored) {
    }

    final double count = meterRegistry.counter("executor.tasks.failed", "name", "test-executor").count();
    assertThat(count, is(equalTo(1.0)));
  }

  @Test
  void testExecuteDoesNotIncrementSucceededCounterOnException() {
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    final Runnable failingCommand = () -> {
      throw new RuntimeException("boom");
    };

    decorator.execute(failingCommand);
    verify(delegateMock).execute(captor.capture());

    try {
      captor.getValue().run();
    } catch (final RuntimeException ignored) {
    }

    final double count = meterRegistry.counter("executor.tasks.succeeded", "name", "test-executor").count();
    assertThat(count, is(equalTo(0.0)));
  }

  @Test
  void testExecuteRethrowsExceptionFromCommand() {
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    final RuntimeException expectedException = new RuntimeException("test error");
    final Runnable failingCommand = () -> {
      throw expectedException;
    };

    decorator.execute(failingCommand);
    verify(delegateMock).execute(captor.capture());

    RuntimeException thrown = null;
    try {
      captor.getValue().run();
    } catch (final RuntimeException ex) {
      thrown = ex;
    }

    assertThat(thrown, is(equalTo(expectedException)));
  }

  @Test
  void testExecuteDecrementsActiveTaskCountAfterSuccessfulRun() {
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

    decorator.execute(() -> {
    });
    verify(delegateMock).execute(captor.capture());
    captor.getValue().run();

    final double gaugeValue = meterRegistry.get("executor.active").tag("name", "test-executor").gauge().value();
    assertThat(gaugeValue, is(equalTo(0.0)));
  }

  @Test
  void testExecuteDecrementsActiveTaskCountAfterFailedRun() {
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    final Runnable failingCommand = () -> {
      throw new RuntimeException("fail");
    };

    decorator.execute(failingCommand);
    verify(delegateMock).execute(captor.capture());

    try {
      captor.getValue().run();
    } catch (final RuntimeException ignored) {
    }

    final double gaugeValue = meterRegistry.get("executor.active").tag("name", "test-executor").gauge().value();
    assertThat(gaugeValue, is(equalTo(0.0)));
  }

  @Test
  void testExecuteRecordsTaskDuration() {
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

    decorator.execute(() -> {
    });
    verify(delegateMock).execute(captor.capture());
    captor.getValue().run();

    final long timerCount = meterRegistry.get("executor.task.duration").tag("name", "test-executor").timer().count();
    assertThat(timerCount, is(equalTo(1L)));
  }

  @Test
  void testExecuteRecordsTaskDurationEvenOnException() {
    final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    final Runnable failingCommand = () -> {
      throw new RuntimeException("fail");
    };

    decorator.execute(failingCommand);
    verify(delegateMock).execute(captor.capture());

    try {
      captor.getValue().run();
    } catch (final RuntimeException ignored) {
    }

    final long timerCount = meterRegistry.get("executor.task.duration").tag("name", "test-executor").timer().count();
    assertThat(timerCount, is(equalTo(1L)));
  }

  @Test
  void testExecuteMultipleSuccessesAccumulateCount() {
    final int runs = 5;
    for (int i = 0; i < runs; i++) {
      final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
      decorator.execute(() -> {
      });
      verify(delegateMock, atLeast(1)).execute(captor.capture());
      captor.getValue().run();
    }

    final double count = meterRegistry.counter("executor.tasks.succeeded", "name", "test-executor").count();
    assertThat(count, is(equalTo((double) runs)));
  }

  @Test
  void testExecuteMultipleFailuresAccumulateCount() {
    final Runnable failingCommand = () -> {
      throw new RuntimeException("fail");
    };
    final int runs = 3;

    for (int i = 0; i < runs; i++) {
      final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
      decorator.execute(failingCommand);
      verify(delegateMock, atLeast(1)).execute(captor.capture());
      try {
        captor.getValue().run();
      } catch (final RuntimeException ignored) {
      }
    }

    final double count = meterRegistry.counter("executor.tasks.failed", "name", "test-executor").count();
    assertThat(count, is(equalTo((double) runs)));
  }

  @Test
  void testActiveGaugeIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("executor.active").tag("name", "test-executor").gauge(), is(notNullValue()));
  }

  @Test
  void testSucceededCounterIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("executor.tasks.succeeded").tag("name", "test-executor").counter(), is(notNullValue()));
  }

  @Test
  void testFailedCounterIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("executor.tasks.failed").tag("name", "test-executor").counter(), is(notNullValue()));
  }

  @Test
  void testTaskTimerIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("executor.task.duration").tag("name", "test-executor").timer(), is(notNullValue()));
  }

  @Test
  void testInvokeAnyPropagatesInterruptedException() throws InterruptedException, ExecutionException {
    final Collection<Callable<String>> tasks = Collections.singletonList(mock(Callable.class));
    when(delegateMock.invokeAny(tasks)).thenThrow(new InterruptedException());

    assertThrows(InterruptedException.class, () -> decorator.invokeAny(tasks));
  }

  @Test
  void testInvokeAnyPropagatesExecutionException() throws InterruptedException, ExecutionException {
    final Collection<Callable<String>> tasks = Collections.singletonList(mock(Callable.class));
    when(delegateMock.invokeAny(tasks)).thenThrow(new ExecutionException(new RuntimeException()));

    assertThrows(ExecutionException.class, () -> decorator.invokeAny(tasks));
  }

  @Test
  void testInvokeAnyWithTimeoutPropagatesTimeoutException() throws InterruptedException, ExecutionException, TimeoutException {
    final Collection<Callable<String>> tasks = Collections.singletonList(mock(Callable.class));
    when(delegateMock.invokeAny(tasks, 1L, TimeUnit.SECONDS)).thenThrow(new TimeoutException());

    assertThrows(TimeoutException.class, () -> decorator.invokeAny(tasks, 1L, TimeUnit.SECONDS));
  }

  @Test
  void testInvokeAllPropagatesInterruptedException() throws InterruptedException {
    final Collection<Callable<String>> tasks = Collections.singletonList(mock(Callable.class));
    when(delegateMock.invokeAll(tasks)).thenThrow(new InterruptedException());

    assertThrows(InterruptedException.class, () -> decorator.invokeAll(tasks));
  }

  @Test
  void testInvokeAllWithTimeoutPropagatesInterruptedException() throws InterruptedException {
    final Collection<Callable<String>> tasks = Collections.singletonList(mock(Callable.class));
    when(delegateMock.invokeAll(tasks, 1L, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

    assertThrows(InterruptedException.class, () -> decorator.invokeAll(tasks, 1L, TimeUnit.SECONDS));
  }

  @Test
  void testAwaitTerminationPropagatesInterruptedException() throws InterruptedException {
    when(delegateMock.awaitTermination(anyLong(), any())).thenThrow(new InterruptedException());

    assertThrows(InterruptedException.class, () -> decorator.awaitTermination(1L, TimeUnit.SECONDS));
  }

  @Test
  void testShutdownNowReturnsEmptyListWhenNoPendingTasks() {
    when(delegateMock.shutdownNow()).thenReturn(Collections.emptyList());

    final List<Runnable> result = decorator.shutdownNow();

    assertThat(result.isEmpty(), is(true));
  }
}