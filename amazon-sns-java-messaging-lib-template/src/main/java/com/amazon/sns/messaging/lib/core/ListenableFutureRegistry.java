
/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.sns.messaging.lib.core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

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

  public CompletableFuture<Object> completable() {
    return CompletableFuture.anyOf(completableFutureSuccess, completableFutureFailure);
  }

}
// @formatter:on
