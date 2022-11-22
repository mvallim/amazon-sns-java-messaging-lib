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
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RequestEntry<T> {

  @Builder.Default
  @ToString.Include
  private final long createTime = System.nanoTime();

  @Builder.Default
  @ToString.Include
  private final String id = UUID.randomUUID().toString();

  @ToString.Include
  private T value;

  @Builder.Default
  @ToString.Include
  private final Map<String, Object> messageHeaders = Collections.emptyMap();

  @ToString.Include
  private String subject;

  @ToString.Include
  private String groupId;

  @ToString.Include
  private String deduplicationId;

}
