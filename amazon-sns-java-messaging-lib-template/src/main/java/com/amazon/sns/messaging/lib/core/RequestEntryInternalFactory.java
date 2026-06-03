/*
 * Copyright 2023 the original author or authors.
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

package com.amazon.sns.messaging.lib.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

// @formatter:off
/**
 * Factory for creating internal request entry representations and computing payload sizes.
 */
@RequiredArgsConstructor
final class RequestEntryInternalFactory {

  private final ObjectMapper objectMapper;

  /**
   * Creates an internal request entry from a request entry and its serialized payload.
   *
   * @param requestEntry the source request entry
   * @param bytes        the serialized payload bytes
   * @return a new internal request entry
   */
  public RequestEntryInternal create(final RequestEntry<?> requestEntry, final byte[] bytes) {
    return RequestEntryInternal.builder()
      .withCreateTime(requestEntry.getCreateTime())
      .withDeduplicationId(requestEntry.getDeduplicationId())
      .withGroupId(requestEntry.getGroupId())
      .withId(requestEntry.getId())
      .withMessageHeaders(requestEntry.getMessageHeaders())
      .withValue(ByteBuffer.wrap(bytes))
      .withSubject(requestEntry.getSubject())
      .build();
  }

  /**
   * Creates an internal request entry, serializing the payload automatically.
   *
   * @param requestEntry the source request entry
   * @return a new internal request entry with serialized payload
   */
  public RequestEntryInternal create(final RequestEntry<?> requestEntry) {
    return create(requestEntry, convertPayload(requestEntry));
  }

  /**
   * Converts the payload of a request entry to a byte array. Strings are converted
   * using UTF-8; other types are serialized via Jackson ObjectMapper.
   *
   * @param requestEntry the request entry whose payload to convert
   * @return the serialized payload bytes
   */
  @SneakyThrows
  public byte[] convertPayload(final RequestEntry<?> requestEntry) {
    return requestEntry.getValue() instanceof String
      ? String.class.cast(requestEntry.getValue()).getBytes(StandardCharsets.UTF_8)
      : objectMapper.writeValueAsBytes(requestEntry.getValue());
  }

  /**
   * Computes the total size of message attributes for a request entry.
   *
   * @param requestEntry the request entry
   * @return the combined size (in bytes) of all attribute keys and values
   */
  @SneakyThrows
  public Integer messageAttributesSize(final RequestEntry<?> requestEntry) {
    final Map<String, Integer> messageAttributes = MessageAttributesInternal.INSTANCE.messageAttributes(requestEntry.getMessageHeaders());

    final Integer messageAttributesKeysSize = messageAttributes.keySet().stream()
      .map(String::length)
      .collect(Collectors.summingInt(Number::intValue));

    final Integer messageAttributesValuesSize = messageAttributes.values().stream()
      .collect(Collectors.summingInt(Number::intValue));

    return messageAttributesKeysSize + messageAttributesValuesSize;
  }

  /**
   * Internal representation of a batched request entry with a serialized payload.
   *
   * @param <E> the payload type (unused here, payload is serialized to bytes)
   */
  @Getter
  @ToString
  @RequiredArgsConstructor
  @Builder(setterPrefix = "with")
  static class RequestEntryInternal {

    /** The creation timestamp in nanoseconds. */
    private final long createTime;

    /** The unique identifier of the request. */
    private final String id;

    /** The serialized payload as a byte buffer. */
    @Getter(value = AccessLevel.PRIVATE)
    private final ByteBuffer value;

    /** Optional message attributes / headers. */
    private final Map<String, Object> messageHeaders;

    /** Optional subject line for the message. */
    private final String subject;

    /** The message group ID for FIFO topics. */
    private final String groupId;

    /** The message deduplication ID for FIFO topics. */
    private final String deduplicationId;

    /**
     * Returns the size of the serialized payload in bytes.
     *
     * @return the payload size
     */
    public int size() {
      return value.capacity();
    }

    /**
     * Decodes the payload as a UTF-8 string.
     *
     * @return the decoded message
     */
    public String getMessage() {
      return StandardCharsets.UTF_8.decode(value).toString();
    }

  }

  @SuppressWarnings("java:S6548")
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  static class MessageAttributesInternal extends AbstractMessageAttributes<Integer> {

    /**
     * Singleton instance of the internal message attributes calculator.
     */

    public static final MessageAttributesInternal INSTANCE = new MessageAttributesInternal();

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getEnumMessageAttribute(final Enum<?> value) {
      return value.name().length();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getStringMessageAttribute(final String value) {
      return value.length();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getNumberMessageAttribute(final Number value) {
      return value.toString().length();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getBinaryMessageAttribute(final ByteBuffer value) {
      return value.remaining();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getStringArrayMessageAttribute(final List<?> values) {
      return stringArray(values).length();
    }

  }

}
// @formatter:on
