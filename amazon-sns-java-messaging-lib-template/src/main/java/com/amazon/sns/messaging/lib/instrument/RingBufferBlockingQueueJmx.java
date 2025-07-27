package com.amazon.sns.messaging.lib.instrument;

import com.amazon.sns.messaging.lib.concurrent.RingBufferBlockingQueue;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RingBufferBlockingQueueJmx implements RingBufferBlockingQueueJmxMBean {

  private final RingBufferBlockingQueue<?> ringBufferBlockingQueue;

  @Override
  public int capacity() {
    return this.ringBufferBlockingQueue.capacity();
  }

  @Override
  public int size() {
    return this.ringBufferBlockingQueue.size();
  }

  @Override
  public boolean isEmpty() {
    return this.ringBufferBlockingQueue.isEmpty();
  }

  @Override
  public boolean isFull() {
    return this.ringBufferBlockingQueue.isFull();
  }

  @Override
  public long writeSequence() {
    return this.ringBufferBlockingQueue.writeSequence();
  }

  @Override
  public long readSequence() {
    return this.ringBufferBlockingQueue.readSequence();
  }

}
