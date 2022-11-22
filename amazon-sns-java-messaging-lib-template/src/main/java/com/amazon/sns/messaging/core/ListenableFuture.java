
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
