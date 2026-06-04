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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class BlockingQueueMetricsDecoratorTest {

  @Mock
  private BlockingQueue<String> delegateMock;

  private MeterRegistry meterRegistry;

  private BlockingQueueMetricsDecorator<String> decorator;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    decorator = new BlockingQueueMetricsDecorator<>(delegateMock, meterRegistry, "test-queue");
  }

  @Test
  void testConstructorWithNullRegistryDoesNotThrow() {
    final BlockingQueueMetricsDecorator<String> instance = new BlockingQueueMetricsDecorator<>(delegateMock, null, "test-queue");
    assertThat(instance, is(notNullValue()));
  }

  @Test
  void testConstructorWithValidRegistryCreatesInstance() {
    assertThat(decorator, is(notNullValue()));
  }

  @Test
  void testPutDelegatesToDelegate() throws InterruptedException {
    decorator.put("element");
    verify(delegateMock).put("element");
  }

  @Test
  void testPutIncrementsPutsTotalOnSuccess() throws InterruptedException {
    decorator.put("element");
    final double count = meterRegistry.counter("blocking.queue.puts.total", "name", "test-queue").count();
    assertThat(count, is(equalTo(1.0)));
  }

  @Test
  void testPutDoesNotIncrementPutsFailedOnSuccess() throws InterruptedException {
    decorator.put("element");
    final double count = meterRegistry.counter("blocking.queue.puts.failed", "name", "test-queue").count();
    assertThat(count, is(equalTo(0.0)));
  }

  @Test
  void testPutIncrementsPutsFailedOnException() throws InterruptedException {
    doThrow(new InterruptedException()).when(delegateMock).put(any());
    assertThrows(InterruptedException.class, () -> decorator.put("element"));
    final double count = meterRegistry.counter("blocking.queue.puts.failed", "name", "test-queue").count();
    assertThat(count, is(equalTo(1.0)));
  }

  @Test
  void testPutDoesNotIncrementPutsTotalOnException() throws InterruptedException {
    doThrow(new InterruptedException()).when(delegateMock).put(any());
    assertThrows(InterruptedException.class, () -> decorator.put("element"));
    final double count = meterRegistry.counter("blocking.queue.puts.total", "name", "test-queue").count();
    assertThat(count, is(equalTo(0.0)));
  }

  @Test
  void testPutRethrowsInterruptedException() throws InterruptedException {
    doThrow(new InterruptedException()).when(delegateMock).put(any());
    assertThrows(InterruptedException.class, () -> decorator.put("element"));
  }

  @Test
  void testPutRecordsDuration() throws InterruptedException {
    decorator.put("element");
    final long count = meterRegistry.get("blocking.queue.put.duration").tag("name", "test-queue").timer().count();
    assertThat(count, is(equalTo(1L)));
  }

  @Test
  void testPutRecordsDurationEvenOnException() throws InterruptedException {
    doThrow(new InterruptedException()).when(delegateMock).put(any());
    assertThrows(InterruptedException.class, () -> decorator.put("element"));
    final long count = meterRegistry.get("blocking.queue.put.duration").tag("name", "test-queue").timer().count();
    assertThat(count, is(equalTo(1L)));
  }

  @Test
  void testPutMultipleSuccessesAccumulateCount() throws InterruptedException {
    decorator.put("a");
    decorator.put("b");
    decorator.put("c");
    final double count = meterRegistry.counter("blocking.queue.puts.total", "name", "test-queue").count();
    assertThat(count, is(equalTo(3.0)));
  }

  @Test
  void testTakeDelegatesToDelegate() throws InterruptedException {
    when(delegateMock.take()).thenReturn("element");
    decorator.take();
    verify(delegateMock).take();
  }

  @Test
  void testTakeReturnsValueFromDelegate() throws InterruptedException {
    when(delegateMock.take()).thenReturn("element");
    final String result = decorator.take();
    assertThat(result, is(equalTo("element")));
  }

  @Test
  void testTakeIncrementsTakesTotalOnSuccess() throws InterruptedException {
    when(delegateMock.take()).thenReturn("element");
    decorator.take();
    final double count = meterRegistry.counter("blocking.queue.takes.total", "name", "test-queue").count();
    assertThat(count, is(equalTo(1.0)));
  }

  @Test
  void testTakeDoesNotIncrementTakesFailedOnSuccess() throws InterruptedException {
    when(delegateMock.take()).thenReturn("element");
    decorator.take();
    final double count = meterRegistry.counter("blocking.queue.takes.failed", "name", "test-queue").count();
    assertThat(count, is(equalTo(0.0)));
  }

  @Test
  void testTakeIncrementsTakesFailedOnException() throws InterruptedException {
    when(delegateMock.take()).thenThrow(new InterruptedException());
    assertThrows(InterruptedException.class, () -> decorator.take());
    final double count = meterRegistry.counter("blocking.queue.takes.failed", "name", "test-queue").count();
    assertThat(count, is(equalTo(1.0)));
  }

  @Test
  void testTakeDoesNotIncrementTakesTotalOnException() throws InterruptedException {
    when(delegateMock.take()).thenThrow(new InterruptedException());
    assertThrows(InterruptedException.class, () -> decorator.take());
    final double count = meterRegistry.counter("blocking.queue.takes.total", "name", "test-queue").count();
    assertThat(count, is(equalTo(0.0)));
  }

  @Test
  void testTakeRethrowsInterruptedException() throws InterruptedException {
    when(delegateMock.take()).thenThrow(new InterruptedException());
    assertThrows(InterruptedException.class, () -> decorator.take());
  }

  @Test
  void testTakeRecordsDuration() throws InterruptedException {
    when(delegateMock.take()).thenReturn("element");
    decorator.take();
    final long count = meterRegistry.get("blocking.queue.take.duration").tag("name", "test-queue").timer().count();
    assertThat(count, is(equalTo(1L)));
  }

  @Test
  void testTakeRecordsDurationEvenOnException() throws InterruptedException {
    when(delegateMock.take()).thenThrow(new InterruptedException());
    assertThrows(InterruptedException.class, () -> decorator.take());
    final long count = meterRegistry.get("blocking.queue.take.duration").tag("name", "test-queue").timer().count();
    assertThat(count, is(equalTo(1L)));
  }

  @Test
  void testTakeMultipleSuccessesAccumulateCount() throws InterruptedException {
    when(delegateMock.take()).thenReturn("a", "b", "c");
    decorator.take();
    decorator.take();
    decorator.take();
    final double count = meterRegistry.counter("blocking.queue.takes.total", "name", "test-queue").count();
    assertThat(count, is(equalTo(3.0)));
  }

  @Test
  void testSizeDelegatesToDelegate() {
    when(delegateMock.size()).thenReturn(5);
    assertThat(decorator.size(), is(equalTo(5)));
    verify(delegateMock).size();
  }

  @Test
  void testIsEmptyReturnsTrueWhenDelegateReturnsTrue() {
    when(delegateMock.isEmpty()).thenReturn(true);
    assertThat(decorator.isEmpty(), is(true));
  }

  @Test
  void testIsEmptyReturnsFalseWhenDelegateReturnsFalse() {
    when(delegateMock.isEmpty()).thenReturn(false);
    assertThat(decorator.isEmpty(), is(false));
  }

  @Test
  void testPeekDelegatesToDelegate() {
    when(delegateMock.peek()).thenReturn("element");
    assertThat(decorator.peek(), is(equalTo("element")));
    verify(delegateMock).peek();
  }

  @Test
  void testPeekReturnsNullWhenDelegateReturnsNull() {
    when(delegateMock.peek()).thenReturn(null);
    assertThat(decorator.peek(), is(equalTo(null)));
  }

  @Test
  void testOfferDelegatesToDelegate() {
    when(delegateMock.offer("element")).thenReturn(true);
    assertThat(decorator.offer("element"), is(true));
    verify(delegateMock).offer("element");
  }

  @Test
  void testOfferReturnsFalseWhenDelegateReturnsFalse() {
    when(delegateMock.offer("element")).thenReturn(false);
    assertThat(decorator.offer("element"), is(false));
  }

  @Test
  void testOfferWithTimeoutDelegatesToDelegate() throws InterruptedException {
    when(delegateMock.offer("element", 5L, TimeUnit.SECONDS)).thenReturn(true);
    assertThat(decorator.offer("element", 5L, TimeUnit.SECONDS), is(true));
    verify(delegateMock).offer("element", 5L, TimeUnit.SECONDS);
  }

  @Test
  void testOfferWithTimeoutReturnsFalseWhenDelegateReturnsFalse() throws InterruptedException {
    when(delegateMock.offer("element", 5L, TimeUnit.SECONDS)).thenReturn(false);
    assertThat(decorator.offer("element", 5L, TimeUnit.SECONDS), is(false));
  }

  @Test
  void testOfferWithTimeoutPropagatesInterruptedException() throws InterruptedException {
    when(delegateMock.offer(any(), anyLong(), any())).thenThrow(new InterruptedException());
    assertThrows(InterruptedException.class, () -> decorator.offer("element", 5L, TimeUnit.SECONDS));
  }

  @Test
  void testPollDelegatesToDelegate() {
    when(delegateMock.poll()).thenReturn("element");
    assertThat(decorator.poll(), is(equalTo("element")));
    verify(delegateMock).poll();
  }

  @Test
  void testPollReturnsNullWhenDelegateReturnsNull() {
    when(delegateMock.poll()).thenReturn(null);
    assertThat(decorator.poll(), is(equalTo(null)));
  }

  @Test
  void testPollWithTimeoutDelegatesToDelegate() throws InterruptedException {
    when(delegateMock.poll(5L, TimeUnit.SECONDS)).thenReturn("element");
    assertThat(decorator.poll(5L, TimeUnit.SECONDS), is(equalTo("element")));
    verify(delegateMock).poll(5L, TimeUnit.SECONDS);
  }

  @Test
  void testPollWithTimeoutPropagatesInterruptedException() throws InterruptedException {
    when(delegateMock.poll(anyLong(), any())).thenThrow(new InterruptedException());
    assertThrows(InterruptedException.class, () -> decorator.poll(5L, TimeUnit.SECONDS));
  }

  @Test
  void testIteratorDelegatesToDelegate() {
    final Iterator<String> expectedIterator = mock(Iterator.class);
    when(delegateMock.iterator()).thenReturn(expectedIterator);
    assertThat(decorator.iterator(), is(equalTo(expectedIterator)));
    verify(delegateMock).iterator();
  }

  @Test
  void testAddDelegatesToDelegate() {
    when(delegateMock.add("element")).thenReturn(true);
    assertThat(decorator.add("element"), is(true));
    verify(delegateMock).add("element");
  }

  @Test
  void testAddReturnsFalseWhenDelegateReturnsFalse() {
    when(delegateMock.add("element")).thenReturn(false);
    assertThat(decorator.add("element"), is(false));
  }

  @Test
  void testRemainingCapacityDelegatesToDelegate() {
    when(delegateMock.remainingCapacity()).thenReturn(10);
    assertThat(decorator.remainingCapacity(), is(equalTo(10)));
    verify(delegateMock).remainingCapacity();
  }

  @Test
  void testDrainToDelegatesToDelegate() {
    final List<String> collection = new java.util.ArrayList<>();
    when(delegateMock.drainTo(collection)).thenReturn(3);
    assertThat(decorator.drainTo(collection), is(equalTo(3)));
    verify(delegateMock).drainTo(collection);
  }

  @Test
  void testDrainToWithMaxElementsDelegatesToDelegate() {
    final List<String> collection = new java.util.ArrayList<>();
    when(delegateMock.drainTo(collection, 5)).thenReturn(5);
    assertThat(decorator.drainTo(collection, 5), is(equalTo(5)));
    verify(delegateMock).drainTo(collection, 5);
  }

  @Test
  void testRemoveDelegatesToDelegate() {
    when(delegateMock.remove()).thenReturn("element");
    assertThat(decorator.remove(), is(equalTo("element")));
    verify(delegateMock).remove();
  }

  @Test
  void testRemoveObjectDelegatesToDelegate() {
    when(delegateMock.remove("element")).thenReturn(true);
    assertThat(decorator.remove("element"), is(true));
    verify(delegateMock).remove("element");
  }

  @Test
  void testRemoveObjectReturnsFalseWhenDelegateReturnsFalse() {
    when(delegateMock.remove("element")).thenReturn(false);
    assertThat(decorator.remove("element"), is(false));
  }

  @Test
  void testElementDelegatesToDelegate() {
    when(delegateMock.element()).thenReturn("element");
    assertThat(decorator.element(), is(equalTo("element")));
    verify(delegateMock).element();
  }

  @Test
  void testToArrayDelegatesToDelegate() {
    final Object[] expected = new Object[] { "a", "b" };
    when(delegateMock.toArray()).thenReturn(expected);
    assertThat(decorator.toArray(), is(equalTo(expected)));
    verify(delegateMock).toArray();
  }

  @Test
  void testToArrayWithTypeDelegatesToDelegate() {
    final String[] input = new String[0];
    final String[] expected = new String[] { "a", "b" };
    when(delegateMock.toArray(input)).thenReturn(expected);
    assertThat(decorator.toArray(input), is(equalTo(expected)));
    verify(delegateMock).toArray(input);
  }

  @Test
  void testContainsAllDelegatesToDelegate() {
    final Collection<String> c = Arrays.asList("a", "b");
    when(delegateMock.containsAll(c)).thenReturn(true);
    assertThat(decorator.containsAll(c), is(true));
    verify(delegateMock).containsAll(c);
  }

  @Test
  void testContainsAllReturnsFalseWhenDelegateReturnsFalse() {
    final Collection<String> c = Arrays.asList("a", "b");
    when(delegateMock.containsAll(c)).thenReturn(false);
    assertThat(decorator.containsAll(c), is(false));
  }

  @Test
  void testAddAllDelegatesToDelegate() {
    final Collection<String> c = Arrays.asList("a", "b");
    when(delegateMock.addAll(c)).thenReturn(true);
    assertThat(decorator.addAll(c), is(true));
    verify(delegateMock).addAll(c);
  }

  @Test
  void testRemoveAllDelegatesToDelegate() {
    final Collection<String> c = Arrays.asList("a", "b");
    when(delegateMock.removeAll(c)).thenReturn(true);
    assertThat(decorator.removeAll(c), is(true));
    verify(delegateMock).removeAll(c);
  }

  @Test
  void testRetainAllDelegatesToDelegate() {
    final Collection<String> c = Arrays.asList("a", "b");
    when(delegateMock.retainAll(c)).thenReturn(true);
    assertThat(decorator.retainAll(c), is(true));
    verify(delegateMock).retainAll(c);
  }

  @Test
  void testClearDelegatesToDelegate() {
    decorator.clear();
    verify(delegateMock).clear();
  }

  @Test
  void testContainsDelegatesToDelegate() {
    when(delegateMock.contains("element")).thenReturn(true);
    assertThat(decorator.contains("element"), is(true));
    verify(delegateMock).contains("element");
  }

  @Test
  void testContainsReturnsFalseWhenDelegateReturnsFalse() {
    when(delegateMock.contains("element")).thenReturn(false);
    assertThat(decorator.contains("element"), is(false));
  }

  @Test
  void testPutsTotalCounterIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("blocking.queue.puts.total").tag("name", "test-queue").counter(), is(notNullValue()));
  }

  @Test
  void testPutsFailedCounterIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("blocking.queue.puts.failed").tag("name", "test-queue").counter(), is(notNullValue()));
  }

  @Test
  void testPutDurationTimerIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("blocking.queue.put.duration").tag("name", "test-queue").timer(), is(notNullValue()));
  }

  @Test
  void testTakesTotalCounterIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("blocking.queue.takes.total").tag("name", "test-queue").counter(), is(notNullValue()));
  }

  @Test
  void testTakesFailedCounterIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("blocking.queue.takes.failed").tag("name", "test-queue").counter(), is(notNullValue()));
  }

  @Test
  void testTakeDurationTimerIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("blocking.queue.take.duration").tag("name", "test-queue").timer(), is(notNullValue()));
  }

  @Test
  void testSizeGaugeIsRegisteredWithCorrectName() {
    assertThat(meterRegistry.get("blocking.queue.size").tag("name", "test-queue").gauge(), is(notNullValue()));
  }

  @Test
  void testSizeGaugeReflectsDelegateSize() {
    when(delegateMock.size()).thenReturn(7);
    final double gaugeValue = meterRegistry.get("blocking.queue.size").tag("name", "test-queue").gauge().value();
    assertThat(gaugeValue, is(equalTo(7.0)));
  }

  @Test
  void testPutAndTakeCountersAreIndependent() throws InterruptedException {
    when(delegateMock.take()).thenReturn("element");
    decorator.put("element");
    decorator.take();

    final double puts = meterRegistry.counter("blocking.queue.puts.total", "name", "test-queue").count();
    final double takes = meterRegistry.counter("blocking.queue.takes.total", "name", "test-queue").count();
    assertThat(puts, is(equalTo(1.0)));
    assertThat(takes, is(equalTo(1.0)));
  }

  @Test
  void testPutsFailedAndTakesFailedAreIndependent() throws InterruptedException {
    doThrow(new InterruptedException()).when(delegateMock).put(any());
    when(delegateMock.take()).thenThrow(new InterruptedException());

    assertThrows(InterruptedException.class, () -> decorator.put("element"));
    assertThrows(InterruptedException.class, () -> decorator.take());

    final double putsFailed = meterRegistry.counter("blocking.queue.puts.failed", "name", "test-queue").count();
    final double takesFailed = meterRegistry.counter("blocking.queue.takes.failed", "name", "test-queue").count();
    assertThat(putsFailed, is(equalTo(1.0)));
    assertThat(takesFailed, is(equalTo(1.0)));
  }
}