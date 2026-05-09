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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractMessageAttributesTest {

  private AbstractMessageAttributes<String> messageAttributes;

  @BeforeEach
  void setUp() {
    messageAttributes = new AbstractMessageAttributes<>() {

      @Override
      protected String getEnumMessageAttribute(final Enum<?> value) {
        return "ENUM:" + value.name();
      }

      @Override
      protected String getStringMessageAttribute(final String value) {
        return "STRING:" + value;
      }

      @Override
      protected String getNumberMessageAttribute(final Number value) {
        return "NUMBER:" + value;
      }

      @Override
      protected String getBinaryMessageAttribute(final ByteBuffer value) {
        return "BINARY:" + value;
      }

      @Override
      protected String getStringArrayMessageAttribute(final List<?> value) {
        return "ARRAY:" + value;
      }
    };
  }

  @Test
  void testMessageAttributesReturnsEmptyMapForEmptyHeaders() {
    final Map<String, String> result = messageAttributes.messageAttributes(Map.of());

    assertThat(result, is(anEmptyMap()));
  }

  @Test
  void testMessageAttributesMapsStringValue() {
    final Map<String, String> result = messageAttributes.messageAttributes(Map.of("key", "hello"));

    assertThat(result, hasEntry("key", "STRING:hello"));
  }

  @Test
  void testMessageAttributesMapsEnumValue() {
    final Map<String, String> result = messageAttributes.messageAttributes(Map.of("key", Thread.State.RUNNABLE));

    assertThat(result, hasEntry("key", "ENUM:RUNNABLE"));
  }

  @Test
  void testMessageAttributesMapsNumberValue() {
    final Map<String, String> result = messageAttributes.messageAttributes(Map.of("key", 42));

    assertThat(result, hasEntry("key", "NUMBER:42"));
  }

  @Test
  void testMessageAttributesMapsByteBufferValue() {
    final ByteBuffer buffer = ByteBuffer.wrap(new byte[] { 1, 2, 3 });
    final Map<String, String> result = messageAttributes.messageAttributes(Map.of("key", buffer));

    assertThat(result, hasEntry("key", "BINARY:" + buffer));
  }

  @Test
  void testMessageAttributesMapsListValue() {
    final List<String> list = List.of("a", "b");
    final Map<String, String> result = messageAttributes.messageAttributes(Map.of("key", list));

    assertThat(result, hasEntry("key", "ARRAY:" + list));
  }

  @Test
  void testMessageAttributesIgnoresUnsupportedValueType() {
    final Map<String, Object> headers = Map.of("key", new Object());

    final Map<String, String> result = messageAttributes.messageAttributes(headers);

    assertThat(result, is(anEmptyMap()));
  }

  @Test
  void testMessageAttributesHandlesMultipleHeadersOfDifferentTypes() {
    final Map<String, Object> headers = Map.of("strKey", "hello", "numKey", 99, "enumKey", Thread.State.BLOCKED);

    final Map<String, String> result = messageAttributes.messageAttributes(headers);

    assertThat(result, hasEntry("strKey", "STRING:hello"));
    assertThat(result, hasEntry("numKey", "NUMBER:99"));
    assertThat(result, hasEntry("enumKey", "ENUM:BLOCKED"));
  }

  @Test
  void testStringArrayFormatsStringsWithQuotes() {
    final String result = AbstractMessageAttributes.stringArray(List.of("foo", "bar"));

    assertThat(result, is("[ \"foo\", \"bar\" ]"));
  }

  @Test
  void testStringArrayFiltersOutNonStringElements() {
    final String result = AbstractMessageAttributes.stringArray(List.of("valid", 123, "also-valid"));

    assertThat(result, is("[ \"valid\", \"also-valid\" ]"));
  }

  @Test
  void testStringArrayReturnsEmptyBracketsForEmptyList() {
    final String result = AbstractMessageAttributes.stringArray(List.of());

    assertThat(result, is("[  ]"));
  }

  @Test
  void testStringArrayReturnsEmptyBracketsWhenNoStringElementsPresent() {
    final String result = AbstractMessageAttributes.stringArray(List.of(1, 2, 3));

    assertThat(result, is("[  ]"));
  }

}