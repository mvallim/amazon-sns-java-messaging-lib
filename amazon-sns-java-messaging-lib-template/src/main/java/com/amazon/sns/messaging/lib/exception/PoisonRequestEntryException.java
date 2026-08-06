package com.amazon.sns.messaging.lib.exception;

public abstract class PoisonRequestEntryException extends Exception {

  private static final long serialVersionUID = -1884047816775456709L;

  protected PoisonRequestEntryException(final String string) {
    super(string);
  }

  protected PoisonRequestEntryException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static PoisonRequestEntryException fromMaximumAllowedMessage(final String message) {
    return new MaximumAllowedMessageException(message);
  }

  public static PoisonRequestEntryException fromJsonProcessing(final String message, final Throwable throwable) {
    return new JsonProcessingException(message, throwable);
  }

}
