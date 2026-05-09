package com.amazon.sns.messaging.lib.exception;

import com.amazon.sns.messaging.lib.model.RequestEntry;

import lombok.Getter;

/**
 * Thrown when a message exceeds the maximum allowed size of 256 KB for Amazon SNS.
 * Contains the offending {@link RequestEntry} for diagnostic purposes.
 */
@Getter
@SuppressWarnings({ "rawtypes", "unchecked" })
public class MaximumAllowedMessageException extends RuntimeException {

  private static final long serialVersionUID = -529663449633021689L;

  private final RequestEntry request;

  /**
   * Constructs a new exception with the given detail message and the offending request.
   *
   * @param string  the detail message
   * @param request the request entry that exceeded the size limit
   */
  public MaximumAllowedMessageException(final String string, final RequestEntry request) {
    super(string);
    this.request = request;
  }

  /**
   * Returns the request entry that caused the exception.
   *
   * @param <T> the payload type of the request
   * @return the oversized request entry
   */
  public <T> RequestEntry<T> getRequest() {
    return request;
  }

}
