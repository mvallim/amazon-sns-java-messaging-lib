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
import lombok.Locked;
import lombok.Setter;
import lombok.SneakyThrows;

/**
 * A bounded blocking queue backed by a ring buffer (circular array). Supports blocking
 * {@link #put(Object)} and {@link #take()} operations. Other {@link BlockingQueue} methods
 * throw {@link UnsupportedOperationException}.
 *
 * @param <E> the type of elements held in this queue
 */
public class RingBufferBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

  /** Default capacity when no explicit capacity is provided. */
  private static final int DEFAULT_CAPACITY = 2048;

  /** The ring buffer array holding queue entries. */
  private final AtomicReferenceArray<Entry<E>> buffer;

  /** The fixed maximum number of elements the queue can hold. */
  private final int capacity;

  /** Sequence number tracking the next write position (starts at -1 indicating no writes). */
  private final AtomicLong writeSequence = new AtomicLong(-1);

  /** Sequence number tracking the next read position. */
  private final AtomicLong readSequence = new AtomicLong(0);

  /** Current number of elements in the queue. */
  private final AtomicInteger size = new AtomicInteger(0);

  /** Fair reentrant lock for coordinating producer/consumer access. */
  private final ReentrantLock reentrantLock;

  /** Condition for consumers waiting when the queue is empty. */
  private final Condition waitingConsumer;

  /** Condition for producers waiting when the queue is full. */
  private final Condition waitingProducer;

  /**
   * Creates a ring buffer with the specified capacity.
   *
   * @param capacity the maximum number of elements the queue can hold
   */
  public RingBufferBlockingQueue(final int capacity) {
    this.capacity = capacity;
    buffer = new AtomicReferenceArray<>(capacity);
    reentrantLock = new ReentrantLock(true);
    waitingConsumer = reentrantLock.newCondition();
    waitingProducer = reentrantLock.newCondition();
    IntStream.range(0, capacity).forEach(idx -> buffer.set(idx, new Entry<>()));
  }

  /**
   * Creates a ring buffer with the default capacity of 2048.
   */
  public RingBufferBlockingQueue() {
    this(RingBufferBlockingQueue.DEFAULT_CAPACITY);
  }

  /**
   * Prevents sequence overflow by wrapping around when the maximum long value is reached.
   *
   * @param sequence the current sequence value
   * @return the sequence value, wrapped if necessary
   */
  private long avoidSequenceOverflow(final long sequence) {
    return (sequence < Long.MAX_VALUE ? sequence : wrap(sequence));
  }

  /**
   * Wraps a sequence number to a valid buffer index.
   *
   * @param sequence the sequence number to wrap
   * @return the buffer index
   */
  private int wrap(final long sequence) {
    return Math.toIntExact(sequence % capacity);
  }

  /**
   * Returns the fixed capacity of this ring buffer.
   *
   * @return the capacity
   */
  public int capacity() {
    return capacity;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size() {
    return size.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEmpty() {
    return size.get() == 0;
  }

  /**
   * Returns whether the queue is full.
   *
   * @return true if the queue size equals its capacity
   */
  public boolean isFull() {
    return size.get() >= capacity;
  }

  /**
   * Returns the current write sequence number.
   *
   * @return the write sequence
   */
  public long writeSequence() {
    return writeSequence.get();
  }

  /**
   * Returns the current read sequence number.
   *
   * @return the read sequence
   */
  public long readSequence() {
    return readSequence.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E peek() {
    return isEmpty() ? null : buffer.get(wrap(readSequence.get())).getValue();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SneakyThrows
  @Locked("reentrantLock")
  public void put(final E element) {
    while (isFull()) {
      waitingProducer.await();
    }

    final long prevWriteSeq = writeSequence.get();
    final long nextWriteSeq = avoidSequenceOverflow(prevWriteSeq) + 1;

    buffer.get(wrap(nextWriteSeq)).setValue(element);

    writeSequence.compareAndSet(prevWriteSeq, nextWriteSeq);

    size.incrementAndGet();

    waitingConsumer.signal();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SneakyThrows
  @Locked("reentrantLock")
  public E take() {
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
  }

  /**
   * @throws UnsupportedOperationException always
   */
  @Override
  public boolean offer(final E element) {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException always
   */
  @Override
  public boolean offer(final E element, final long timeout, final TimeUnit unit) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException always
   */
  @Override
  public E poll() {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException always
   */
  @Override
  public E poll(final long timeout, final TimeUnit unit) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException always
   */
  @Override
  public Iterator<E> iterator() {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException always
   */
  @Override
  public boolean add(final E element) {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException always
   */
  @Override
  public int remainingCapacity() {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException always
   */
  @Override
  public int drainTo(final Collection<? super E> collection) {
    throw new UnsupportedOperationException();
  }

  /**
   * @throws UnsupportedOperationException always
   */
  @Override
  public int drainTo(final Collection<? super E> collection, final int maxElements) {
    throw new UnsupportedOperationException();
  }

  /**
   * Internal entry wrapper that holds a value within the ring buffer.
   *
   * @param <E> the type of the value
   */
  @Getter
  @Setter
  static class Entry<E> {

    private E value;

  }

}
