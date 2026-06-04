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

package com.amazon.sns.messaging.lib.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.core.RequestEntryInternalFactory.MessageAttributesInternal;
import com.amazon.sns.messaging.lib.core.RequestEntryInternalFactory.RequestEntryInternal;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

// @formatter:off
class RequestEntryInternalFactoryTest {

  private ObjectMapper objectMapper;
  private RequestEntryInternalFactory factory;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    factory = new RequestEntryInternalFactory(objectMapper);
  }

  private RequestEntry<Object> buildRequestEntry(final Object payload, final Map<String, Object> headers) {
    return RequestEntry.builder()
      .withValue(payload)
      .withId("test-id")
      .withSubject("test-subject")
      .withGroupId("group-1")
      .withDeduplicationId("dedup-1")
      .withMessageHeaders(headers)
      .build();
  }

  private RequestEntry<Object> buildMinimalRequestEntry(final Object payload) {
    return buildRequestEntry(payload, new HashMap<>());
  }

  @Nested
  class CreateWithBytes {

    @Test
    void testCreateWithBytesReturnsNotNull() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("hello");
      final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result, is(notNullValue()));
    }

    @Test
    void testCreateWithBytesMapsId() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("hello");
      final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.getId(), equalTo("test-id"));
    }

    @Test
    void testCreateWithBytesMapsSubject() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("hello");
      final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.getSubject(), equalTo("test-subject"));
    }

    @Test
    void testCreateWithBytesMapsGroupId() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("hello");
      final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.getGroupId(), equalTo("group-1"));
    }

    @Test
    void testCreateWithBytesMapsDeduplicationId() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("hello");
      final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.getDeduplicationId(), equalTo("dedup-1"));
    }

    @Test
    void testCreateWithBytesMapsMessageHeaders() {
      final Map<String, Object> headers = new HashMap<>();
      headers.put("headerKey", "headerValue");
      final RequestEntry<Object> entry = buildRequestEntry("hello", headers);
      final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.getMessageHeaders(), equalTo(headers));
    }

    @Test
    void testCreateWithBytesPayloadSizeMatchesByteArrayLength() {
      final byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
      final RequestEntry<Object> entry = buildMinimalRequestEntry("hello world");

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.size(), equalTo(bytes.length));
    }

    @Test
    void testCreateWithBytesPayloadDecodedCorrectly() {
      final String message = "hello world";
      final byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
      final RequestEntry<Object> entry = buildMinimalRequestEntry(message);

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.getMessage(), equalTo(message));
    }

    @Test
    void testCreateWithBytesCreateTimeIsSet() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("hello");
      final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.getCreateTime(), greaterThan(0L));
    }

    @Test
    void testCreateWithEmptyBytesPayloadSizeIsZero() {
      final byte[] bytes = new byte[0];
      final RequestEntry<Object> entry = buildMinimalRequestEntry("");

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.size(), equalTo(0));
    }

    @Test
    void testCreateWithNullHeadersMapsNullHeaders() {
      final RequestEntry<Object> entry = buildRequestEntry("hello", null);
      final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

      final RequestEntryInternal result = factory.create(entry, bytes);

      assertThat(result.getMessageHeaders(), is(nullValue()));
    }
  }

  @Nested
  class CreateAutoSerialize {

    @Test
    void testCreateWithStringPayloadReturnsNotNull() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("hello");

      final RequestEntryInternal result = factory.create(entry);

      assertThat(result, is(notNullValue()));
    }

    @Test
    void testCreateWithStringPayloadDecodesCorrectly() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("hello");

      final RequestEntryInternal result = factory.create(entry);

      assertThat(result.getMessage(), equalTo("hello"));
    }

    @Test
    void testCreateWithStringPayloadSizeMatchesUtf8Length() {
      final String message = "hello";
      final RequestEntry<Object> entry = buildMinimalRequestEntry(message);

      final RequestEntryInternal result = factory.create(entry);

      assertThat(result.size(), equalTo(message.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void testCreateWithMultibyteStringPayloadEncodedInUtf8() {
      final String message = "こんにちは";
      final RequestEntry<Object> entry = buildMinimalRequestEntry(message);

      final RequestEntryInternal result = factory.create(entry);

      assertThat(result.size(), equalTo(message.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void testCreateWithObjectPayloadSerializesViaJackson() throws Exception {
      final Map<String, String> payload = new HashMap<>();
      payload.put("key", "value");
      final RequestEntry<Object> entry = buildMinimalRequestEntry(payload);

      final RequestEntryInternal result = factory.create(entry);

      final String decoded = result.getMessage();
      final Map<?, ?> parsed = objectMapper.readValue(decoded, Map.class);
      assertThat(parsed.get("key"), equalTo("value"));
    }

    @Test
    void testCreateWithIntegerPayloadSerializesViaJackson() throws Exception {
      final RequestEntry<Object> entry = buildMinimalRequestEntry(42);

      final RequestEntryInternal result = factory.create(entry);

      assertThat(result.getMessage(), equalTo("42"));
    }

    @Test
    void testCreateWithEmptyStringPayloadSizeIsZero() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("");

      final RequestEntryInternal result = factory.create(entry);

      assertThat(result.size(), equalTo(0));
    }

    @Test
    void testCreateWithStringPayloadMapsId() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("payload");

      final RequestEntryInternal result = factory.create(entry);

      assertThat(result.getId(), equalTo("test-id"));
    }
  }

  @Nested
  class ConvertPayload {

    @Test
    void testConvertPayloadStringReturnsUtf8Bytes() {
      final String value = "hello";
      final RequestEntry<Object> entry = buildMinimalRequestEntry(value);

      final byte[] result = factory.convertPayload(entry);

      assertThat(result, equalTo(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testConvertPayloadStringIsNotSerializedWithJacksonQuotes() {
      final String value = "hello";
      final RequestEntry<Object> entry = buildMinimalRequestEntry(value);

      final byte[] result = factory.convertPayload(entry);

      assertThat(new String(result, StandardCharsets.UTF_8), equalTo("hello"));
    }

    @Test
    void testConvertPayloadObjectUsesJackson() throws Exception {
      final Map<String, String> payload = Collections.singletonMap("a", "b");
      final RequestEntry<Object> entry = buildMinimalRequestEntry(payload);

      final byte[] result = factory.convertPayload(entry);

      final Map<?, ?> parsed = objectMapper.readValue(result, Map.class);
      assertThat(parsed.get("a"), equalTo("b"));
    }

    @Test
    void testConvertPayloadListUsesJackson() throws Exception {
      final List<String> payload = Arrays.asList("x", "y", "z");
      final RequestEntry<Object> entry = buildMinimalRequestEntry(payload);

      final byte[] result = factory.convertPayload(entry);

      final List<?> parsed = objectMapper.readValue(result, List.class);
      assertThat(parsed.size(), equalTo(3));
    }

    @Test
    void testConvertPayloadMultibyteStringEncodedCorrectly() {
      final String value = "日本語";
      final RequestEntry<Object> entry = buildMinimalRequestEntry(value);

      final byte[] result = factory.convertPayload(entry);

      assertThat(result, equalTo(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testConvertPayloadEmptyStringReturnsEmptyArray() {
      final RequestEntry<Object> entry = buildMinimalRequestEntry("");

      final byte[] result = factory.convertPayload(entry);

      assertThat(result.length, equalTo(0));
    }
  }

  @Nested
  class MessageAttributesSize {

    @Test
    void testMessageAttributesSizeWithEmptyHeadersReturnsZero() {
      final RequestEntry<Object> entry = buildRequestEntry("hello", new HashMap<>());

      final Integer result = factory.messageAttributesSize(entry);

      assertThat(result, equalTo(0));
    }

    @Test
    void testMessageAttributesSizeWithStringAttributeCountsKeyAndValue() {
      final Map<String, Object> headers = new HashMap<>();
      headers.put("key", "value");

      final RequestEntry<Object> entry = buildRequestEntry("hello", headers);

      final Integer result = factory.messageAttributesSize(entry);

      assertThat(result, equalTo(8));
    }

    @Test
    void testMessageAttributesSizeWithMultipleAttributesSumsAll() {
      final Map<String, Object> headers = new HashMap<>();
      headers.put("k1", "val");
      headers.put("k2", "valu");

      final RequestEntry<Object> entry = buildRequestEntry("hello", headers);

      final Integer result = factory.messageAttributesSize(entry);

      assertThat(result, equalTo(11));
    }

    @Test
    void testMessageAttributesSizeWithNumberAttribute() {
      final Map<String, Object> headers = new HashMap<>();
      headers.put("num", 12345);

      final RequestEntry<Object> entry = buildRequestEntry("hello", headers);

      final Integer result = factory.messageAttributesSize(entry);

      assertThat(result, greaterThan(0));
    }

    @Test
    void testMessageAttributesSizeWithEnumAttribute() {
      final Map<String, Object> headers = new HashMap<>();
      headers.put("e", SampleEnum.VALUE_ONE);

      final RequestEntry<Object> entry = buildRequestEntry("hello", headers);

      final Integer result = factory.messageAttributesSize(entry);

      assertThat(result, equalTo(10));
    }

    @Test
    void testMessageAttributesSizeWithBinaryAttribute() {
      final byte[] bytes = new byte[] { 1, 2, 3, 4 };
      final Map<String, Object> headers = new HashMap<>();
      headers.put("bin", ByteBuffer.wrap(bytes));

      final RequestEntry<Object> entry = buildRequestEntry("hello", headers);

      final Integer result = factory.messageAttributesSize(entry);

      assertThat(result, equalTo(7));
    }

    @Test
    void testMessageAttributesSizeWithStringListAttribute() {
      final Map<String, Object> headers = new HashMap<>();
      headers.put("arr", Arrays.asList("a", "b", "c"));

      final RequestEntry<Object> entry = buildRequestEntry("hello", headers);

      final Integer result = factory.messageAttributesSize(entry);

      assertThat(result, greaterThan(0));
    }

    @Test
    void testMessageAttributesSizeIsNonNegative() {
      final Map<String, Object> headers = new HashMap<>();
      headers.put("x", "y");

      final RequestEntry<Object> entry = buildRequestEntry("hello", headers);

      final Integer result = factory.messageAttributesSize(entry);

      assertThat(result, greaterThanOrEqualTo(0));
    }
  }

  @Nested
  class RequestEntryInternalTest {

    @Test
    void testSizeReturnsBufferCapacity() {
      final byte[] bytes = "test payload".getBytes(StandardCharsets.UTF_8);
      final RequestEntryInternal internal = RequestEntryInternal.builder()
        .withId("id")
        .withValue(ByteBuffer.wrap(bytes))
        .withCreateTime(System.nanoTime())
        .build();

      assertThat(internal.size(), equalTo(bytes.length));
    }

    @Test
    void testGetMessageDecodesUtf8Correctly() {
      final String message = "decoded message";
      final RequestEntryInternal internal = RequestEntryInternal.builder()
        .withId("id")
        .withValue(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)))
        .withCreateTime(System.nanoTime())
        .build();

      assertThat(internal.getMessage(), equalTo(message));
    }

    @Test
    void testGetMessageDecodesMultibyteUtf8Correctly() {
      final String message = "日本語テスト";
      final RequestEntryInternal internal = RequestEntryInternal.builder()
        .withId("id")
        .withValue(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)))
        .withCreateTime(System.nanoTime())
        .build();

      assertThat(internal.getMessage(), equalTo(message));
    }

    @Test
    void testBuilderSetsAllFieldsCorrectly() {
      final Map<String, Object> headers = Collections.singletonMap("h", "v");
      final long now = System.nanoTime();
      final RequestEntryInternal internal = RequestEntryInternal.builder()
        .withId("my-id")
        .withSubject("my-subject")
        .withGroupId("my-group")
        .withDeduplicationId("my-dedup")
        .withMessageHeaders(headers)
        .withValue(ByteBuffer.wrap("data".getBytes(StandardCharsets.UTF_8)))
        .withCreateTime(now)
        .build();

      assertThat(internal.getId(), equalTo("my-id"));
      assertThat(internal.getSubject(), equalTo("my-subject"));
      assertThat(internal.getGroupId(), equalTo("my-group"));
      assertThat(internal.getDeduplicationId(), equalTo("my-dedup"));
      assertThat(internal.getMessageHeaders(), equalTo(headers));
      assertThat(internal.getCreateTime(), equalTo(now));
    }

    @Test
    void testSizeWithEmptyBufferIsZero() {
      final RequestEntryInternal internal = RequestEntryInternal.builder()
        .withId("id")
        .withValue(ByteBuffer.wrap(new byte[0]))
        .withCreateTime(System.nanoTime())
        .build();

      assertThat(internal.size(), equalTo(0));
    }

    @Test
    void testToStringIsNotNull() {
      final RequestEntryInternal internal = RequestEntryInternal.builder()
        .withId("id")
        .withValue(ByteBuffer.wrap("x".getBytes(StandardCharsets.UTF_8)))
        .withCreateTime(1L)
        .build();

      assertThat(internal.toString(), is(notNullValue()));
    }
  }

  @Nested
  class MessageAttributesInternalTest {

    private final MessageAttributesInternal instance = MessageAttributesInternal.INSTANCE;

    @Test
    void testSingletonInstanceIsNotNull() {
      assertThat(instance, is(notNullValue()));
    }

    @Test
    void testSingletonInstanceIsSameObject() {
      assertThat(MessageAttributesInternal.INSTANCE, is(instance));
    }

    @Test
    void testGetEnumMessageAttributeReturnsNameLength() {
      final Integer result = instance.getEnumMessageAttribute(SampleEnum.VALUE_ONE);

      assertThat(result, equalTo("VALUE_ONE".length()));
    }

    @Test
    void testGetEnumMessageAttributeShortName() {
      final Integer result = instance.getEnumMessageAttribute(SampleEnum.A);

      assertThat(result, equalTo(1));
    }

    @Test
    void testGetStringMessageAttributeReturnsLength() {
      final Integer result = instance.getStringMessageAttribute("hello");

      assertThat(result, equalTo(5));
    }

    @Test
    void testGetStringMessageAttributeEmptyString() {
      final Integer result = instance.getStringMessageAttribute("");

      assertThat(result, equalTo(0));
    }

    @Test
    void testGetNumberMessageAttributeInteger() {
      final Integer result = instance.getNumberMessageAttribute(12345);

      assertThat(result, equalTo(5));
    }

    @Test
    void testGetNumberMessageAttributeFloat() {
      final Integer result = instance.getNumberMessageAttribute(3.14f);

      assertThat(result, equalTo(String.valueOf(3.14f).length()));
    }

    @Test
    void testGetNumberMessageAttributeNegativeNumber() {
      final Integer result = instance.getNumberMessageAttribute(-99);

      assertThat(result, equalTo(3));
    }

    @Test
    void testGetBinaryMessageAttributeReturnsRemaining() {
      final byte[] bytes = new byte[] { 10, 20, 30 };
      final ByteBuffer buffer = ByteBuffer.wrap(bytes);

      final Integer result = instance.getBinaryMessageAttribute(buffer);

      assertThat(result, equalTo(3));
    }

    @Test
    void testGetBinaryMessageAttributeEmptyBuffer() {
      final ByteBuffer buffer = ByteBuffer.wrap(new byte[0]);

      final Integer result = instance.getBinaryMessageAttribute(buffer);

      assertThat(result, equalTo(0));
    }

    @Test
    void testGetStringArrayMessageAttributeReturnsCombinedLength() {
      final List<String> values = Arrays.asList("a", "b", "c");

      final Integer result = instance.getStringArrayMessageAttribute(values);

      assertThat(result, greaterThan(0));
    }

    @Test
    void testGetStringArrayMessageAttributeEmptyList() {
      final Integer result = instance.getStringArrayMessageAttribute(Collections.emptyList());

      assertThat(result, greaterThanOrEqualTo(0));
    }
  }

  private enum SampleEnum {
    A, VALUE_ONE
  }

}