package com.amazon.sns.messaging.core;

import java.util.function.Consumer;

// @formatter:off
public interface ListenableFuture<S, F> {

  void addCallback(final Consumer<? super S> successCallback, final Consumer<? super F> failureCallback);

  default void addCallback(final Consumer<? super S> successCallback) {
    addCallback(successCallback, failureCallback -> { });
  }

}
// @formatter:on
