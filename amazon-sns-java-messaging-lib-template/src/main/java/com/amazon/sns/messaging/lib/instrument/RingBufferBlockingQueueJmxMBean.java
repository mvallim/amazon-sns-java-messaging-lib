package com.amazon.sns.messaging.lib.instrument;

public interface RingBufferBlockingQueueJmxMBean {

  int capacity();

  int size();

  boolean isEmpty();

  boolean isFull();

  long writeSequence();

  long readSequence();

}
