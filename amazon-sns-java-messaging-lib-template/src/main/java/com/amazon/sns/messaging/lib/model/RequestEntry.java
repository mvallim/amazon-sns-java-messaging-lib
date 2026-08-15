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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Represents a single message request to be published to an Amazon SNS topic.
 * Contains the message payload, metadata, and optional FIFO-related
 * identifiers.
 *
 * @param <T> the type of the message payload
 */
@Getter
@ToString
@Builder(toBuilder = true, setterPrefix = "with")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestEntry<T> {

  /**
   * The creation timestamp in nanoseconds (from {@link System#nanoTime}).
   */
  @NonNull
  @Builder.Default
  private final Long createTime = System.nanoTime();

  /**
   * A unique identifier for this request.
   */
  @NonNull
  @Builder.Default
  private final String id = UUID.randomUUID().toString();

  /**
   * The message payload value.
   */
  private final T value;

  /**
   * Optional message attributes / headers.
   */
  @Builder.Default
  private final Map<String, Object> messageHeaders = Collections.emptyMap();

  /**
   * An optional subject line for the message.
   */
  private final String subject;

  /**
   * The message group ID for FIFO topics.
   */
  private final String groupId;

  /**
   * The message deduplication ID for FIFO topics.
   */
  private final String deduplicationId;

}
