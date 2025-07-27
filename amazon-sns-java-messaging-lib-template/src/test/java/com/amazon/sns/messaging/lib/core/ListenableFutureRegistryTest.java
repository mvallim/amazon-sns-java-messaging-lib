/*
 * Copyright 2024 the original author or authors.
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

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

// @formatter:off
class ListenableFutureRegistryTest {

  @Test
  void testSuccessWithCallbacks() {
    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> assertThat(entry, notNullValue());
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureRegistry listenableFutureRegistry = new ListenableFutureRegistry();

    listenableFutureRegistry.addCallback(successCallback, failureCallback);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));
    listenableFutureRegistry.fail(mock(ResponseFailEntry.class));

    assertThat(listenableFutureRegistry.completable(), notNullValue());
  }

  @Test
  void testSuccessWithCallback() {
    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureRegistry listenableFutureRegistry = new ListenableFutureRegistry();

    listenableFutureRegistry.addCallback(successCallback);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));

    assertThat(listenableFutureRegistry.completable(), notNullValue());
  }

  @Test
  void testSuccessWithouCallback() {
    final ListenableFutureRegistry listenableFutureRegistry = new ListenableFutureRegistry();

    listenableFutureRegistry.addCallback(null);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));

    assertThat(listenableFutureRegistry.completable(), notNullValue());
  }

  @Test
  void testSuccessWithouCallbacks() {
    final ListenableFutureRegistry listenableFutureRegistry = new ListenableFutureRegistry();

    listenableFutureRegistry.addCallback(null, null);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));
    listenableFutureRegistry.fail(mock(ResponseFailEntry.class));

    assertThat(listenableFutureRegistry.completable(), notNullValue());
  }

}
// @formatter:on
