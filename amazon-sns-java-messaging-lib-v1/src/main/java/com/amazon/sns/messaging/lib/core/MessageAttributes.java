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
import java.util.List;

import com.amazonaws.services.sns.model.MessageAttributeValue;

// @formatter:off
class MessageAttributes extends AbstractMessageAttributes<MessageAttributeValue> {

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
