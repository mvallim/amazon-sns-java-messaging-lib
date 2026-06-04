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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockingSubmissionPolicyTest {

  @Mock(strictness = Strictness.LENIENT)
  private ThreadPoolExecutor executorMock;

  @Mock(strictness = Strictness.LENIENT)
  private BlockingQueue<Runnable> queueMock;

  private BlockingSubmissionPolicy policy;

  @BeforeEach
  void setUp() {
    policy = new BlockingSubmissionPolicy(30000);
    when(executorMock.getQueue()).thenReturn(queueMock);
  }

  @Test
  void testConstructorCreatesInstance() {
    assertThat(new BlockingSubmissionPolicy(1000), is(notNullValue()));
  }

  @Test
  void testImplementsRejectedExecutionHandler() {
    assertThat(policy, is(instanceOf(RejectedExecutionHandler.class)));
  }

  @Test
  void testRejectedExecutionOffersRunnableToQueue() throws InterruptedException {
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenReturn(true);

    policy.rejectedExecution(task, executorMock);

    verify(queueMock).offer(eq(task), eq(30000L), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  void testRejectedExecutionUsesConfiguredTimeout() throws InterruptedException {
    final long customTimeout = 5000L;
    final BlockingSubmissionPolicy customPolicy = new BlockingSubmissionPolicy(customTimeout);
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenReturn(true);

    customPolicy.rejectedExecution(task, executorMock);

    verify(queueMock).offer(eq(task), eq(customTimeout), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  void testRejectedExecutionUsesMillisecondsTimeUnit() throws InterruptedException {
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenReturn(true);

    policy.rejectedExecution(task, executorMock);

    verify(queueMock).offer(any(), anyLong(), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  void testRejectedExecutionSucceedsWhenQueueAcceptsTask() throws InterruptedException {
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenReturn(true);

    policy.rejectedExecution(task, executorMock);

    verify(executorMock).getQueue();
  }

  @Test
  void testRejectedExecutionThrowsRejectedExecutionExceptionWhenQueueReturnsFalse() throws InterruptedException {
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenReturn(false);

    assertThrows(RejectedExecutionException.class, () -> policy.rejectedExecution(task, executorMock));
  }

  @Test
  void testRejectedExecutionExceptionMessageIsTimeout() throws InterruptedException {
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenReturn(false);

    final RejectedExecutionException thrown = assertThrows(RejectedExecutionException.class, () -> policy.rejectedExecution(task, executorMock));

    assertThat(thrown.getMessage(), is("Timeout"));
  }

  @Test
  void testRejectedExecutionPropagatesInterruptedException() throws InterruptedException {
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenThrow(new InterruptedException());

    assertThrows(InterruptedException.class, () -> policy.rejectedExecution(task, executorMock));
  }

  @Test
  void testRejectedExecutionRetrievesQueueFromExecutor() throws InterruptedException {
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenReturn(true);

    policy.rejectedExecution(task, executorMock);

    verify(executorMock).getQueue();
  }

  @Test
  void testRejectedExecutionWithZeroTimeoutThrowsWhenQueueFull() throws InterruptedException {
    final BlockingSubmissionPolicy zeroTimeoutPolicy = new BlockingSubmissionPolicy(0);
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenReturn(false);

    assertThrows(RejectedExecutionException.class, () -> zeroTimeoutPolicy.rejectedExecution(task, executorMock));
  }

  @Test
  void testRejectedExecutionWithZeroTimeoutSucceedsWhenQueueAccepts() throws InterruptedException {
    final BlockingSubmissionPolicy zeroTimeoutPolicy = new BlockingSubmissionPolicy(0);
    final Runnable task = mock(Runnable.class);
    when(queueMock.offer(any(), anyLong(), any())).thenReturn(true);

    zeroTimeoutPolicy.rejectedExecution(task, executorMock);

    verify(queueMock).offer(eq(task), eq(0L), eq(TimeUnit.MILLISECONDS));
  }
}