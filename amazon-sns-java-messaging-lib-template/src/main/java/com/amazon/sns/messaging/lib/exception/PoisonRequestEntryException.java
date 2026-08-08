package com.amazon.sns.messaging.lib.exception;

/**
 * Base exception for request entries that cannot be published and must be dropped.
 * A "poison" request entry is one that fails serialization or exceeds the maximum
 * allowed message size, making it impossible to include in a publish batch.
 * <p>
 * Concrete subclasses ({@link MaximumAllowedMessageException},
 * {@link JsonProcessingException}) are created via the factory methods
 * {@link #fromMaximumAllowedMessage(String)} and
 * {@link #fromJsonProcessing(String, Throwable)}.
 */
public abstract class PoisonRequestEntryException extends Exception {

  private static final long serialVersionUID = -1884047816775456709L;

  /**
   * Creates a new poison request entry exception.
   *
   * @param string the detail message
   */
  protected PoisonRequestEntryException(final String string) {
    super(string);
  }

  /**
   * Creates a new poison request entry exception with a cause.
   *
   * @param message the detail message
   * @param cause   the underlying cause of the failure
   */
  protected PoisonRequestEntryException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a {@link MaximumAllowedMessageException} for a message that exceeds the
   * maximum allowed size.
   *
   * @param message the detail message
   * @return a new {@link MaximumAllowedMessageException}
   */
  public static PoisonRequestEntryException fromMaximumAllowedMessage(final String message) {
    return new MaximumAllowedMessageException(message);
  }

  /**
   * Creates a {@link JsonProcessingException} for a payload that failed to serialize.
   *
   * @param message   the detail message
   * @param throwable the underlying serialization exception
   * @return a new {@link JsonProcessingException}
   */
  public static PoisonRequestEntryException fromJsonProcessing(final String message, final Throwable throwable) {
    return new JsonProcessingException(message, throwable);
  }

}
