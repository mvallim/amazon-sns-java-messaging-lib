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

package com.amazon.sns.messaging.lib.instrument;

import com.amazon.sns.messaging.lib.concurrent.RingBufferBlockingQueue;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RingBufferBlockingQueueJmx implements RingBufferBlockingQueueJmxMBean {
  
  private final RingBufferBlockingQueue<?> ringBufferBlockingQueue;
  
  @Override
  public int capacity() {
    return ringBufferBlockingQueue.capacity();
  }
  
  @Override
  public int size() {
    return ringBufferBlockingQueue.size();
  }
  
  @Override
  public boolean isEmpty() {
    return ringBufferBlockingQueue.isEmpty();
  }
  
  @Override
  public boolean isFull() {
    return ringBufferBlockingQueue.isFull();
  }
  
  @Override
  public long writeSequence() {
    return ringBufferBlockingQueue.writeSequence();
  }
  
  @Override
  public long readSequence() {
    return ringBufferBlockingQueue.readSequence();
  }
  
}
