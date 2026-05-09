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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractMessageAttributesTest {

  private AbstractMessageAttributes<String> messageAttributes;

  @BeforeEach
  void setUp() {
    messageAttributes = new AbstractMessageAttributes<String>() {

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
    final Map<String, String> result = messageAttributes.messageAttributes(Collections.emptyMap());

    assertThat(result, is(anEmptyMap()));
  }

  @Test
  void testMessageAttributesMapsStringValue() {
    final Map<String, Object> map = new HashMap<>();
    map.put("key", "hello");

    final Map<String, String> result = messageAttributes.messageAttributes(map);

    assertThat(result, hasEntry("key", "STRING:hello"));
  }

  @Test
  void testMessageAttributesMapsEnumValue() {
    final Map<String, Object> map = new HashMap<>();
    map.put("key", Thread.State.RUNNABLE);

    final Map<String, String> result = messageAttributes.messageAttributes(map);

    assertThat(result, hasEntry("key", "ENUM:RUNNABLE"));
  }

  @Test
  void testMessageAttributesMapsNumberValue() {
    final Map<String, Object> map = new HashMap<>();
    map.put("key", 42);

    final Map<String, String> result = messageAttributes.messageAttributes(map);

    assertThat(result, hasEntry("key", "NUMBER:42"));
  }

  @Test
  void testMessageAttributesMapsByteBufferValue() {
    final ByteBuffer buffer = ByteBuffer.wrap(new byte[] { 1, 2, 3 });

    final Map<String, Object> map = new HashMap<>();
    map.put("key", buffer);

    final Map<String, String> result = messageAttributes.messageAttributes(map);

    assertThat(result, hasEntry("key", "BINARY:" + buffer));
  }

  @Test
  void testMessageAttributesMapsListValue() {
    final List<String> list = new LinkedList<>();
    list.add("a");
    list.add("b");

    final Map<String, Object> map = new HashMap<>();
    map.put("key", list);

    final Map<String, String> result = messageAttributes.messageAttributes(map);

    assertThat(result, hasEntry("key", "ARRAY:" + list));
  }

  @Test
  void testMessageAttributesIgnoresUnsupportedValueType() {
    final Map<String, Object> headers = new HashMap<>();
    headers.put("key", new Object());

    final Map<String, String> result = messageAttributes.messageAttributes(headers);

    assertThat(result, is(anEmptyMap()));
  }

  @Test
  void testMessageAttributesHandlesMultipleHeadersOfDifferentTypes() {
    final Map<String, Object> headers = new HashMap<>();
    headers.put("strKey", "hello");
    headers.put("numKey", 99);
    headers.put("enumKey", Thread.State.BLOCKED);

    final Map<String, String> result = messageAttributes.messageAttributes(headers);

    assertThat(result, hasEntry("strKey", "STRING:hello"));
    assertThat(result, hasEntry("numKey", "NUMBER:99"));
    assertThat(result, hasEntry("enumKey", "ENUM:BLOCKED"));
  }

  @Test
  void testStringArrayFormatsStringsWithQuotes() {
    final List<String> list = new LinkedList<>();
    list.add("foo");
    list.add("bar");

    final String result = AbstractMessageAttributes.stringArray(list);

    assertThat(result, is("[ \"foo\", \"bar\" ]"));
  }

  @Test
  void testStringArrayFiltersOutNonStringElements() {
    final List<Object> list = new LinkedList<>();
    list.add("valid");
    list.add(123);
    list.add("also-valid");

    final String result = AbstractMessageAttributes.stringArray(list);

    assertThat(result, is("[ \"valid\", \"also-valid\" ]"));
  }

  @Test
  void testStringArrayReturnsEmptyBracketsForEmptyList() {
    final String result = AbstractMessageAttributes.stringArray(Collections.emptyList());

    assertThat(result, is("[  ]"));
  }

  @Test
  void testStringArrayReturnsEmptyBracketsWhenNoStringElementsPresent() {
    final List<Object> list = new LinkedList<>();
    list.add(1);
    list.add(2);
    list.add(3);

    final String result = AbstractMessageAttributes.stringArray(list);

    assertThat(result, is("[  ]"));
  }

}