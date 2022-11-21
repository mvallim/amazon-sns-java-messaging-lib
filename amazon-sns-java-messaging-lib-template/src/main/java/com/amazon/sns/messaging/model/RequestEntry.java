package com.amazon.sns.messaging.model;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestEntry<T> {

  @Builder.Default
  private long createTime = System.nanoTime();

  @Builder.Default
  private String id = UUID.randomUUID().toString();

  private T value;

  @Builder.Default
  private Map<String, Object> messageHeaders = Collections.emptyMap();

  private String subject;

  private String groupId;

  private String deduplicationId;

}
