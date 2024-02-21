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
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.core.MessageAttributes;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

class MessageAttributesTest {

  private final MessageAttributes messageAttributes = new MessageAttributes();

  @Test
  void testSuccessStringHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("string", "string");

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("string"), is(true));
    assertThat(attributes.get("string").dataType(), is("String"));
    assertThat(attributes.get("string").stringValue(), is("string"));
  }

  @Test
  void testSuccessEnumHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("enum", Cards.A);

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("enum"), is(true));
    assertThat(attributes.get("enum").dataType(), is("String"));
    assertThat(attributes.get("enum").stringValue(), is("A"));
  }

  @Test
  void testSuccessNumberHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("number", 1);

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("number"), is(true));
    assertThat(attributes.get("number").dataType(), is("Number.java.lang.Integer"));
    assertThat(attributes.get("number").stringValue(), is("1"));
  }

  @Test
  void testSuccessBinaryHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("binary", ByteBuffer.wrap(new byte[0]));

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("binary"), is(true));
    assertThat(attributes.get("binary").dataType(), is("Binary"));
    assertThat(attributes.get("binary").binaryValue(), is(SdkBytes.fromByteBuffer(ByteBuffer.wrap(new byte[0]))));
  }

  @Test
  void testSuccessStringArrayHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("stringArray", Arrays.asList("123", 1, new Object(), "456"));

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("stringArray"), is(true));
    assertThat(attributes.get("stringArray").dataType(), is("String.Array"));
    assertThat(attributes.get("stringArray").stringValue(), is("[ \"123\", \"456\" ]"));
  }

  @Test
  void testFailUnsupportedHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("unsupported", new Object());

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("unsupported"), is(false));
  }

  public enum Cards {
    A, J, Q, K;
  }

}
