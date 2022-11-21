package com.amazon.sns.messaging.core;

import java.nio.ByteBuffer;
import java.util.List;

import lombok.SneakyThrows;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

// @formatter:off
public class MessageAttributes extends AbstractMessageAttributes<MessageAttributeValue> {

  @Override
  protected MessageAttributeValue getEnumMessageAttribute(final Enum<?> value) {
    return MessageAttributeValue.builder().dataType(STRING).stringValue(value.name()).build();
  }

  @Override
  protected MessageAttributeValue getStringMessageAttribute(final String value) {
    return MessageAttributeValue.builder().dataType(STRING).stringValue(value).build();
  }

  @Override
  protected MessageAttributeValue getNumberMessageAttribute(final Number value) {
    return MessageAttributeValue.builder().dataType(NUMBER + "." + value.getClass().getName()).stringValue(value.toString()).build();
  }

  @Override
  protected MessageAttributeValue getBinaryMessageAttribute(final ByteBuffer value) {
    return MessageAttributeValue.builder().dataType(BINARY).binaryValue(SdkBytes.fromByteBuffer(value)).build();
  }

  @Override
  @SneakyThrows
  protected MessageAttributeValue getStringArrayMessageAttribute(final List<?> values) {
    return MessageAttributeValue.builder().dataType(STRING_ARRAY).stringValue(stringArray(values)).build();
  }

}
// @formatter:on
