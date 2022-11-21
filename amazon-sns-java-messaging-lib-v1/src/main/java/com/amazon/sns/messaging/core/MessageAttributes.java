package com.amazon.sns.messaging.core;

import java.nio.ByteBuffer;
import java.util.List;

import com.amazonaws.services.sns.model.MessageAttributeValue;

// @formatter:off
public class MessageAttributes extends AbstractMessageAttributes<MessageAttributeValue> {

  @Override
  protected MessageAttributeValue getEnumMessageAttribute(final Enum<?> value) {
    return new MessageAttributeValue().withDataType(STRING).withStringValue(value.name());
  }

  @Override
  protected MessageAttributeValue getStringMessageAttribute(final String value) {
    return new MessageAttributeValue().withDataType(STRING).withStringValue(value);
  }

  @Override
  protected MessageAttributeValue getNumberMessageAttribute(final Number value) {
    return new MessageAttributeValue().withDataType(NUMBER + "." + value.getClass().getName()).withStringValue(value.toString());
  }

  @Override
  protected MessageAttributeValue getBinaryMessageAttribute(final ByteBuffer value) {
    return new MessageAttributeValue().withDataType(BINARY).withBinaryValue(value);
  }

  @Override
  protected MessageAttributeValue getStringArrayMessageAttribute(final List<?> values) {
    return new MessageAttributeValue().withDataType(STRING_ARRAY).withStringValue(stringArray(values));
  }

}
// @formatter:on
