package com.amazon.sns.messaging.lib.exception;

import com.amazon.sns.messaging.lib.model.RequestEntry;

import lombok.Getter;

@Getter
@SuppressWarnings({ "rawtypes", "unchecked" })
public class MaximumAllowedMessageException extends RuntimeException {

  private static final long serialVersionUID = -529663449633021689L;

  private final RequestEntry request;

  public MaximumAllowedMessageException(final String string, final RequestEntry request) {
    super(string);
    this.request = request;
  }

  public <T> RequestEntry<T> getRequest() {
    return request;
  }

}
