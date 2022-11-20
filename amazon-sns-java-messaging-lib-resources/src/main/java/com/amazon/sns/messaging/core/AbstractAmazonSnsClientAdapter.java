package com.amazon.sns.messaging.core;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;

abstract class AbstractAmazonSnsClientAdapter<T> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  protected final T client;

  protected AbstractAmazonSnsClientAdapter(final T client) {
    this.client = client;
  }

  public abstract ListenableFuture<SuccessResult, FailureResult> publish(final Object payload, final Map<String, Object> messageHeaders);

  @SneakyThrows
  protected String convertPayload(final Object payload) {
    return payload instanceof String ? payload.toString() : objectMapper.writeValueAsString(payload);
  }

}
