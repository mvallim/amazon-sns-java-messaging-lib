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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

@SuppressWarnings({ "java:S2274", "unchecked" })
public class RingBufferBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

  private static final int DEFAULT_CAPACITY = 2048;

  private final Entry<E>[] buffer;

  private final int capacity;

  private final AtomicInteger writeSequence = new AtomicInteger(-1);

  private final AtomicInteger readSequence = new AtomicInteger(0);

  private final ReentrantLock reentrantLock;

  private final Condition notEmpty;

  private final Condition notFull;

  public RingBufferBlockingQueue(final int capacity) {
    this.capacity = capacity;
    this.buffer = new Entry[capacity];
    Arrays.setAll(buffer, p -> new Entry<>());
    reentrantLock = new ReentrantLock(true);
    notEmpty = reentrantLock.newCondition();
    notFull = reentrantLock.newCondition();
  }

  public RingBufferBlockingQueue() {
    this(DEFAULT_CAPACITY);
  }

  private void enqueue(final E element) throws InterruptedException {
    while (isFull()) {
      notFull.await();
    }

    final int nextWriteSeq = writeSequence.get() + 1;
    buffer[wrap(nextWriteSeq)].setValue(element);
    writeSequence.incrementAndGet();
    notEmpty.signal();
  }

  private E dequeue() throws InterruptedException {
    while (isEmpty()) {
      notEmpty.await();
    }

    final E nextValue = buffer[wrap(readSequence.get())].getValue();
    readSequence.incrementAndGet();
    notFull.signal();
    return nextValue;
  }

  private int wrap(final int sequence) {
    return sequence % capacity;
  }

  @Override
  public int size() {
    return (writeSequence.get() - readSequence.get()) + 1;
  }

  @Override
  public boolean isEmpty() {
    return writeSequence.get() < readSequence.get();
  }

  public boolean isFull() {
    return size() >= capacity;
  }

  public int writeSequence() {
    return writeSequence.get();
  }

  public int readSequence() {
    return readSequence.get();
  }

  @Override
  public E peek() {
    return isEmpty() ? null : buffer[wrap(readSequence.get())].getValue();
  }

  @Override
  @SneakyThrows
  public void put(final E element) {
    try {
      reentrantLock.lock();
      enqueue(element);
    } finally {
      reentrantLock.unlock();
    }
  }

  @Override
  @SneakyThrows
  public E take() {
    try {
      reentrantLock.lock();
      return dequeue();
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
