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

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

@SuppressWarnings({ "java:S2274" })
public class RingBufferBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

  private static final int DEFAULT_CAPACITY = 2048;

  private final AtomicReferenceArray<Entry<E>> buffer;

  private final int capacity;

  private final AtomicLong writeSequence = new AtomicLong(-1);

  private final AtomicLong readSequence = new AtomicLong(0);

  private final AtomicInteger size = new AtomicInteger(0);

  private final ReentrantLock reentrantLock;

  private final Condition waitingConsumer;

  private final Condition waitingProducer;

  public RingBufferBlockingQueue(final int capacity) {
    this.capacity = capacity;
    buffer = new AtomicReferenceArray<>(capacity);
    reentrantLock = new ReentrantLock(true);
    waitingConsumer = reentrantLock.newCondition();
    waitingProducer = reentrantLock.newCondition();
    IntStream.range(0, capacity).forEach(idx -> buffer.set(idx, new Entry<>()));
  }

  public RingBufferBlockingQueue() {
    this(DEFAULT_CAPACITY);
  }

  private long avoidSequenceOverflow(final long sequence) {
    return (sequence < Long.MAX_VALUE ? sequence : wrap(sequence));
  }

  private int wrap(final long sequence) {
    return Math.toIntExact(sequence % capacity);
  }

  @Override
  public int size() {
    return size.get();
  }

  @Override
  public boolean isEmpty() {
    return size.get() == 0;
  }

  public boolean isFull() {
    return size.get() >= capacity;
  }

  public long writeSequence() {
    return writeSequence.get();
  }

  public long readSequence() {
    return readSequence.get();
  }

  @Override
  public E peek() {
    return isEmpty() ? null : buffer.get(wrap(readSequence.get())).getValue();
  }

  @Override
  @SneakyThrows
  public void put(final E element) {
    try {
      reentrantLock.lock();

      while (isFull()) {
        waitingProducer.await();
      }

      final long prevWriteSeq = writeSequence.get();
      final long nextWriteSeq = avoidSequenceOverflow(prevWriteSeq) + 1;

      buffer.get(wrap(nextWriteSeq)).setValue(element);

      writeSequence.compareAndSet(prevWriteSeq, nextWriteSeq);

      size.incrementAndGet();

      waitingConsumer.signal();
    } finally {
      reentrantLock.unlock();
    }
  }

  @Override
  @SneakyThrows
  public E take() {
    try {
      reentrantLock.lock();

      while (isEmpty()) {
        waitingConsumer.await();
      }

      final long prevReadSeq = readSequence.get();
      final long nextReadSeq = avoidSequenceOverflow(prevReadSeq) + 1;

      final E nextValue = buffer.get(wrap(prevReadSeq)).getValue();

      buffer.get(wrap(prevReadSeq)).setValue(null);

      readSequence.compareAndSet(prevReadSeq, nextReadSeq);

      size.decrementAndGet();

      waitingProducer.signal();

      return nextValue;
    } finally {
      reentrantLock.unlock();
    }
  }

  @Override
  public boolean offer(final E element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean offer(final E element, final long timeout, final TimeUnit unit) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public E poll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public E poll(final long timeout, final TimeUnit unit) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<E> iterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean add(final E element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int remainingCapacity() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int drainTo(final Collection<? super E> collection) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int drainTo(final Collection<? super E> collection, final int maxElements) {
    throw new UnsupportedOperationException();
  }

  @Getter
  @Setter
  static class Entry<E> {

    private E value;

  }

}
