package com.amazon.sns.messaging.core;

import java.util.function.Consumer;

// @formatter:off
public interface ListenableFuture<S, F> {

  void addCallback(final Consumer<S> successCallback, final Consumer<F> failureCallback);

  default void addCallback(final Consumer<S> successCallback) {
    addCallback(successCallback, failureCallback -> { });
  }

}
// @formatter:on
