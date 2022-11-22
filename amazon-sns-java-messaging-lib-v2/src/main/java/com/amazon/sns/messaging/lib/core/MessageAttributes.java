
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

import java.nio.ByteBuffer;
import java.util.List;

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
  protected MessageAttributeValue getStringArrayMessageAttribute(final List<?> values) {
    return MessageAttributeValue.builder().dataType(STRING_ARRAY).stringValue(stringArray(values)).build();
  }

}
// @formatter:on
