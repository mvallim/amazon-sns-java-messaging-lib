package com.amazon.sns.messaging.model;

import java.util.Map;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor
public class RequestEntry<T> {

  private final long createTime = System.nanoTime();

  private final String id = UUID.randomUUID().toString();

  private final T value;

  private final Map<String, Object> messageHeaders;

  private final String subject;

  private final String groupId;

  private final String deduplicationId;

}
