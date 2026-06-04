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

import java.util.function.Consumer;

import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

// @formatter:off
/**
 * A simplified listenable future that supports registering success and failure callbacks.
 * Allows asynchronous notification of operation outcomes.
 *
 * @param <S> the success result type
 * @param <F> the failure result type
 */
public interface ListenableFuture<S, F> {

  /**
   * Registers both success and failure callbacks.
   *
   * @param successCallback the callback to invoke on success (may be null)
   * @param failureCallback the callback to invoke on failure (may be null)
   */
  void addCallback(final Consumer<? super S> successCallback, final Consumer<? super F> failureCallback);

  /**
   * Registers a success callback; failures are silently ignored.
   *
   * @param successCallback the callback to invoke on success
   */
  default void addCallback(final Consumer<? super S> successCallback) {
    addCallback(successCallback, result -> { });
  }

  /**
   * Marks the future as completed successfully with the given entry.
   *
   * @param entry the success result
   */
  void success(final ResponseSuccessEntry entry);

  /**
   * Marks the future as failed with the given entry.
   *
   * @param entry the failure result
   */
  void fail(final ResponseFailEntry entry);

}
// @formatter:on
