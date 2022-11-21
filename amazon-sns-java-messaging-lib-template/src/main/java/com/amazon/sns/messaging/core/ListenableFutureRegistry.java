package com.amazon.sns.messaging.core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.amazon.sns.messaging.model.ResponseFailEntry;
import com.amazon.sns.messaging.model.ResponseSuccessEntry;

// @formatter:off
class ListenableFutureRegistry implements ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> {

  private final CompletableFuture<ResponseSuccessEntry> completableFutureSuccess = new CompletableFuture<>();

  private final CompletableFuture<ResponseFailEntry> completableFutureFailure = new CompletableFuture<>();

  @Override
  public void addCallback(final Consumer<? super ResponseSuccessEntry> successCallback, final Consumer<? super ResponseFailEntry> failureCallback) {
    final Consumer<? super ResponseSuccessEntry> internalSuccessCallback = Objects.nonNull(successCallback) ? successCallback : result -> { };
    final Consumer<? super ResponseFailEntry> internalFailureCallback = Objects.nonNull(failureCallback) ? failureCallback : result -> { };
    completableFutureSuccess.whenComplete((result, throwable) -> internalSuccessCallback.accept(result));
    completableFutureFailure.whenComplete((result, throwable) -> internalFailureCallback.accept(result));
  }

  @Override
  public void addCallback(final Consumer<? super ResponseSuccessEntry> successCallback) {
    addCallback(successCallback, null);
  }

  public void success(final ResponseSuccessEntry entry) {
    completableFutureSuccess.complete(entry);
  }

  public void fail(final ResponseFailEntry entry) {
    completableFutureFailure.complete(entry);
  }

  public CompletableFuture<?> completable() {
    return CompletableFuture.anyOf(completableFutureSuccess, completableFutureFailure);
  }

}
// @formatter:on
