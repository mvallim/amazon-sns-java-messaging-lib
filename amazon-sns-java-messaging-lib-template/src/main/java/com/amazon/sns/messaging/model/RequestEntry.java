package com.amazon.sns.messaging.model;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class RequestEntry<T> {

  @Builder.Default
  private final long createTime = System.nanoTime();

  @Builder.Default
  private final String id = UUID.randomUUID().toString();

  private T value;

  @Builder.Default
  private final Map<String, Object> messageHeaders = Collections.emptyMap();

  private String subject;

  private String groupId;

  private String deduplicationId;

}
