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

import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Locked;

/**
 * A bounded blocking queue backed by a ring buffer (circular array). Supports
 * blocking {@link #put(Object)} and {@link #take()} operations. Other
 * {@link BlockingQueue} methods throw {@link UnsupportedOperationException}.
 *
 * @param <E> the type of elements held in this queue
 */
@SuppressWarnings({ "unchecked", "java:S3078", "java:S1948" })
public class RingBufferBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, Serializable {

  private static final long serialVersionUID = 5440626969571896605L;

  /** Default capacity when no explicit capacity is provided. */
  private static final int DEFAULT_CAPACITY = 2048;

  /** The ring buffer array holding queue entries. */
  private final E[] buffer;

  /** The fixed maximum number of elements the queue can hold. */
  private final int capacity;

  /** Bitmask used to map a monotonically increasing cursor to a slot index. */
  private final int indexMask;

  /** Index of the next buffer slot to be written. */
  private volatile int writeIndex;

  /** Index of the next buffer slot to be read. */
  private volatile int readIndex;

  /** Current number of elements in the queue. */
  private final AtomicInteger size = new AtomicInteger(0);

  /**
   * Lock serializing producer operations, including writes to the ring buffer,
   * updates to the write index, and coordination with waiting producers.
   */
  private final ReentrantLock producerReentrantLock = new ReentrantLock();

  /** Condition for producers waiting when the queue is full. */
  private final Condition waitingProducer = producerReentrantLock.newCondition();

  /**
   * Lock serializing consumer operations, including reads from the ring buffer,
   * updates to the read index, and coordination with waiting consumers.
   */
  private final ReentrantLock consumerReentrantLock = new ReentrantLock();

  /** Condition for consumers waiting when the queue is empty. */
  private final Condition waitingConsumer = consumerReentrantLock.newCondition();

  /**
   * Creates a ring buffer with the specified capacity.
   *
   * @param capacity the maximum number of elements the queue can hold; must be
   *                 positive
   * @throws IllegalArgumentException if {@code capacity <= 0}
   */
  public RingBufferBlockingQueue(final int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive, got: " + capacity);
    }

    this.capacity = nextPowerOfTwo(capacity);
    indexMask = this.capacity - 1;
    buffer = (E[]) new Object[this.capacity];
  }

  /**
   * Creates a ring buffer with the default capacity of 2048.
   */
  public RingBufferBlockingQueue() {
    this(RingBufferBlockingQueue.DEFAULT_CAPACITY);
  }

  /**
   * Rounds the given value up to the next power of two. If the value is already a
   * power of two, it is returned unchanged.
   *
   * @param value the value to round up; must be positive
   * @return the smallest power of two greater than or equal to {@code value}
   */
  private static int nextPowerOfTwo(final int value) {
    final int highestOneBit = Integer.highestOneBit(value);
    return highestOneBit == value ? value : (highestOneBit << 1);
  }

  /**
   * Wakes a single consumer waiting for an element to become available.
   * <p>
   * This method is invoked after a successful insertion when the queue
   * transitions from empty to non-empty. The {@link Locked} annotation ensures
   * that the associated consumer lock is held before signaling the waiting
   * condition.
   */
  @Locked("consumerReentrantLock")
  private void signalConsumer() {
    waitingConsumer.signal();
  }

  /**
   * Wakes a single producer waiting for space to become available.
   * <p>
   * This method is invoked after a successful removal when the queue transitions
   * from full to not-full. The {@link Locked} annotation ensures that the
   * associated producer lock is held before signaling the waiting condition.
   */
  @Locked("producerReentrantLock")
  private void signalProducer() {
    waitingProducer.signal();
  }

  /**
   * Maps the given index to a valid position in the underlying ring buffer.
   * <p>
   * Since the buffer capacity is always a power of two, wrapping is performed
   * efficiently using a bit mask instead of the modulo operator.
   *
   * @param index the logical index to map
   * @return the corresponding slot index in the backing array
   */
  private int index(final int sequence) {
    return sequence & indexMask;
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
    return size.get() == capacity;
  }

  /**
   * Returns the current write index.
   * <p>
   * The returned value identifies the next buffer slot where a producer will
   * insert an element. The value is always in the range {@code [0, capacity())}.
   *
   * @return the current write index
   */
  public long writeIndex() {
    return writeIndex;
  }

  /**
   * Returns the current read index.
   * <p>
   * The returned value identifies the next buffer slot from which a consumer will
   * remove an element. The value is always in the range {@code [0, capacity())}.
   *
   * @return the current read index
   */
  public long readIndex() {
    return readIndex;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns, without removing it, the element at the head of this queue, or
   * {@code null} if the queue is empty.
   *
   * <p>
   * The operation is synchronized with consumer operations to provide a
   * consistent view of the current head element while allowing producers to
   * continue inserting concurrently.
   */
  @Override
  @Locked("consumerReentrantLock")
  public E peek() {
    return isEmpty() ? null : buffer[readIndex];
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * If the queue is full, the calling thread blocks until space becomes available
   * or the thread is interrupted.
   *
   * <p>
   * This operation is serialized with other producers while remaining concurrent
   * with consumers whenever possible.
   *
   * @throws InterruptedException if interrupted while waiting
   */
  @Override
  public void put(final E element) throws InterruptedException {
    Objects.requireNonNull(element, "element");

    int prevSize;

    // Serialize producer operations while allowing consumers to proceed
    // concurrently.
    producerReentrantLock.lockInterruptibly();

    try {

      // Wait until space becomes available.
      while (isFull()) {
        waitingProducer.await();
      }

      // Publish the element into the current write slot.
      buffer[writeIndex] = element;

      // Advance to the next write position.
      writeIndex = index(writeIndex + 1);

      // Atomically publish the insertion by incrementing the element count.
      prevSize = size.getAndIncrement();

      // If additional capacity remains, wake another waiting producer.
      if ((prevSize + 1) < capacity) {
        waitingProducer.signal();
      }
    } finally {
      producerReentrantLock.unlock();
    }

    // If the queue was previously empty, wake one waiting consumer.
    if (prevSize == 0) {
      signalConsumer();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * If the queue is empty, the calling thread blocks until an element becomes
   * available or the thread is interrupted.
   *
   * <p>
   * This operation is serialized with other consumers while remaining concurrent
   * with producers whenever possible.
   *
   * @throws InterruptedException if interrupted while waiting
   */
  @Override
  public E take() throws InterruptedException {
    int prevSize;

    E element;

    // Serialize consumer operations while allowing producers to proceed
    // concurrently.
    consumerReentrantLock.lockInterruptibly();

    try {
      // Wait until an element becomes available.
      while (isEmpty()) {
        waitingConsumer.await();
      }

      // Read the current head element.
      element = buffer[readIndex];

      // Clear the slot to allow the element to be garbage collected.
      buffer[readIndex] = null;

      // Advance to the next read position.
      readIndex = index(readIndex + 1);

      // Atomically publish the removal by decrementing the element count.
      prevSize = size.getAndDecrement();

      // If additional elements remain, wake another waiting consumer.
      if (prevSize > 1) {
        waitingConsumer.signal();
      }
    } finally {
      consumerReentrantLock.unlock();
    }

    // If the queue was previously full, wake one waiting producer.
    if (prevSize == capacity) {
      signalProducer();
    }

    return element;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean offer(final E element) {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean offer(final E element, final long timeout, final TimeUnit unit) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E poll() {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E poll(final long timeout, final TimeUnit unit) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Iterator<E> iterator() {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int remainingCapacity() {
    return capacity - size.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int drainTo(final Collection<? super E> collection) {
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int drainTo(final Collection<? super E> collection, final int maxElements) {
    throw new UnsupportedOperationException();
  }

}
