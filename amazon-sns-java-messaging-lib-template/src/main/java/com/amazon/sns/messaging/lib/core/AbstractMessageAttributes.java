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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

// @formatter:off
abstract class AbstractMessageAttributes<V> {

  protected static final String BINARY = "Binary";

  protected static final String STRING = "String";

  protected static final String NUMBER = "Number";

  protected static final String STRING_ARRAY = "String.Array";

  public Map<String, V> messageAttributes(final Map<String, Object> messageHeaders) {
    final Map<String, V> messageAttributes = new HashMap<>();

    for (final Entry<String, Object> messageHeader : messageHeaders.entrySet()) {
      final String key = messageHeader.getKey();
      final Object value = messageHeader.getValue();

      if (value instanceof Enum) {
        messageAttributes.put(key, getEnumMessageAttribute(Enum.class.cast(value)));
      } else if (value instanceof String) {
        messageAttributes.put(key, getStringMessageAttribute(String.class.cast(value)));
      } else if (value instanceof Number) {
        messageAttributes.put(key, getNumberMessageAttribute(Number.class.cast(value)));
      } else if (value instanceof ByteBuffer) {
        messageAttributes.put(key, getBinaryMessageAttribute(ByteBuffer.class.cast(value)));
      } else if (value instanceof List) {
        messageAttributes.put(key, getStringArrayMessageAttribute(List.class.cast(value)));
      }
    }

    return messageAttributes;
  }

  protected static String stringArray(final List<?> values) {
    final List<String> collect = values.stream()
      .filter(String.class::isInstance)
      .map(String.class::cast)
      .map(value -> "\"" + value + "\"")
      .collect(Collectors.toList());
    return "[ " + String.join(", ", collect) + " ]";
  }

  protected abstract V getEnumMessageAttribute(final Enum<?> value);

  protected abstract V getStringMessageAttribute(final String value);

  protected abstract V getNumberMessageAttribute(final Number value);

  protected abstract V getBinaryMessageAttribute(final ByteBuffer value);

  protected abstract V getStringArrayMessageAttribute(final List<?> value);

}
// @formatter:on
