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
@RequiredArgsConstructor
final class RequestEntryInternalFactory {

  private final ObjectMapper objectMapper;

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

  public RequestEntryInternal create(final RequestEntry<?> requestEntry) {
    return create(requestEntry, convertPayload(requestEntry));
  }

  @SneakyThrows
  public byte[] convertPayload(final RequestEntry<?> requestEntry) {
    return requestEntry.getValue() instanceof String
      ? String.class.cast(requestEntry.getValue()).getBytes(StandardCharsets.UTF_8)
      : objectMapper.writeValueAsBytes(requestEntry.getValue());
  }

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

  @Getter
  @ToString
  @RequiredArgsConstructor
  @Builder(setterPrefix = "with")
  static class RequestEntryInternal {

    private final long createTime;

    private final String id;

    @Getter(value = AccessLevel.PRIVATE)
    private final ByteBuffer value;

    private final Map<String, Object> messageHeaders;

    private final String subject;

    private final String groupId;

    private final String deduplicationId;

    public int size() {
      return value.capacity();
    }

    public String getMessage() {
      return StandardCharsets.UTF_8.decode(value).toString();
    }

  }

  @SuppressWarnings("java:S6548")
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  static class MessageAttributesInternal extends AbstractMessageAttributes<Integer> {

    public static final MessageAttributesInternal INSTANCE = new MessageAttributesInternal();

    @Override
    public Integer getEnumMessageAttribute(final Enum<?> value) {
      return value.name().length();
    }

    @Override
    public Integer getStringMessageAttribute(final String value) {
      return value.length();
    }

    @Override
    public Integer getNumberMessageAttribute(final Number value) {
      return value.toString().length();
    }

    @Override
    public Integer getBinaryMessageAttribute(final ByteBuffer value) {
      return value.remaining();
    }

    @Override
    public Integer getStringArrayMessageAttribute(final List<?> values) {
      return stringArray(values).length();
    }

  }

}
// @formatter:on
