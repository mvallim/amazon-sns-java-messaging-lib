package com.amazon.sns.messaging.core;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

abstract class AbstractMessageAttributes<V> {

  private static final Set<String> skipHeader = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

  static {
    skipHeader.addAll(Set.of("message-group-id", "message-deduplication-id"));
  }

  protected static final String BINARY = "Binary";

  protected static final String STRING = "String";

  protected static final String NUMBER = "Number";

  protected static final String STRING_ARRAY = "String.Array";

  public Map<String, V> messageAttributes(final Map<String, Object> messageHeaders) {
    final Map<String, V> messageAttributes = new HashMap<>();

    for (final Entry<String, Object> messageHeader : messageHeaders.entrySet()) {
      final String key = messageHeader.getKey();
      final Object value = messageHeader.getValue();

      if (skipHeader.contains(key)) {
        continue;
      }

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

  protected abstract V getEnumMessageAttribute(final Enum<?> value);

  protected abstract V getStringMessageAttribute(final String value);

  protected abstract V getNumberMessageAttribute(final Number value);

  protected abstract V getBinaryMessageAttribute(final ByteBuffer value);

  protected abstract V getStringArrayMessageAttribute(final List<?> value);

}
