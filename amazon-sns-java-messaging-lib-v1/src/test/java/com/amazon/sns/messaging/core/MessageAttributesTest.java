package com.amazon.sns.messaging.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.amazonaws.services.sns.model.MessageAttributeValue;

public class MessageAttributesTest {

  private final MessageAttributes messageAttributes = new MessageAttributes();

  @Test
  public void testSuccessStringHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("string", "string");

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("string"), is(true));
    assertThat(attributes.get("string").getDataType(), is("String"));
    assertThat(attributes.get("string").getStringValue(), is("string"));
  }

  @Test
  public void testSuccessEnumHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("enum", Cards.A);

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("enum"), is(true));
    assertThat(attributes.get("enum").getDataType(), is("String"));
    assertThat(attributes.get("enum").getStringValue(), is("A"));
  }

  @Test
  public void testSuccessNumberHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("number", 1);

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("number"), is(true));
    assertThat(attributes.get("number").getDataType(), is("Number.java.lang.Integer"));
    assertThat(attributes.get("number").getStringValue(), is("1"));
  }

  @Test
  public void testSuccessBinaryHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("binary", ByteBuffer.wrap(new byte[0]));

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("binary"), is(true));
    assertThat(attributes.get("binary").getDataType(), is("Binary"));
    assertThat(attributes.get("binary").getBinaryValue(), is(ByteBuffer.wrap(new byte[0])));
  }

  @Test
  public void testSuccessStringArrayHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("stringArray", Arrays.asList("123", 1, new Object(), "456"));

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("stringArray"), is(true));
    assertThat(attributes.get("stringArray").getDataType(), is("String.Array"));
    assertThat(attributes.get("stringArray").getStringValue(), is("[ \"123\", \"456\" ]"));
  }

  @Test
  public void testFailUnsupportedHeader() {
    final Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put("unsupported", new Object());

    final Map<String, MessageAttributeValue> attributes = messageAttributes.messageAttributes(messageHeaders);

    assertThat(attributes.containsKey("unsupported"), is(false));
  }

  public enum Cards {
    A, J, Q, K;
  }

}
