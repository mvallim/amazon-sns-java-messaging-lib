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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Represents a single message request to be published to an Amazon SNS topic.
 * Contains the message payload, metadata, and optional FIFO-related identifiers.
 *
 * @param <T> the type of the message payload
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class RequestEntry<T> {

  /**
   * The creation timestamp in nanoseconds (from {@link System#nanoTime}).
   */
  @Builder.Default
  private final long createTime = System.nanoTime();

  /**
   * A unique identifier for this request.
   */
  @Builder.Default
  private final String id = UUID.randomUUID().toString();

  /**
   * The message payload value.
   */
  private T value;

  /**
   * Optional message attributes / headers.
   */
  @Builder.Default
  private final Map<String, Object> messageHeaders = Collections.emptyMap();

  /**
   * An optional subject line for the message.
   */
  private String subject;

  /**
   * The message group ID for FIFO topics.
   */
  private String groupId;

  /**
   * The message deduplication ID for FIFO topics.
   */
  private String deduplicationId;

}
