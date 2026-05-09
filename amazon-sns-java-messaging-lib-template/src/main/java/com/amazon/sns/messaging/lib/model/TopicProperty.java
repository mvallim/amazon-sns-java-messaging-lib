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

package com.amazon.sns.messaging.lib.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Configuration properties for an Amazon SNS topic.
 */
@Getter
@Builder
@ToString
public class TopicProperty {

  /**
   * Whether the topic is a FIFO topic.
   */
  private final boolean fifo;

  /**
   * The maximum number of threads in the pool for concurrent publishing.
   */
  private final Integer maximumPoolSize;

  /**
   * The ARN of the SNS topic.
   */
  private final String topicArn;

  /**
   * The batching linger time in milliseconds.
   */
  private final long linger;

  /**
   * The maximum number of messages per batch.
   */
  private final int maxBatchSize;

}
