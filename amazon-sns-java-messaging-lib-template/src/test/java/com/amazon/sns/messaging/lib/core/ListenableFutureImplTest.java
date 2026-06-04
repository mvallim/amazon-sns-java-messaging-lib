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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.core.ListenableFutureImpl.State;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

// @formatter:off
class ListenableFutureImplTest {

  @Test
  void testSuccessWithCallbacksBefore() {
    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> assertThat(entry, notNullValue());
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl();

    listenableFutureRegistry.addCallback(successCallback, failureCallback);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));
    listenableFutureRegistry.fail(mock(ResponseFailEntry.class));
  }

  @Test
  void testSuccessWithCallbacksAfter() {
    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> assertThat(entry, notNullValue());
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl();

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));
    listenableFutureRegistry.fail(mock(ResponseFailEntry.class));

    listenableFutureRegistry.addCallback(successCallback, failureCallback);
  }

  @Test
  void testSuccessWithCallbackSuccessBefore() {
    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl();

    listenableFutureRegistry.addCallback(successCallback, null);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));
  }

  @Test
  void testSuccessWithCallbackSuccessAfter() {
    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl();

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));

    listenableFutureRegistry.addCallback(successCallback, null);
  }

  @Test
  void testSuccessWithCallbackFailBefore() {
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl();

    listenableFutureRegistry.addCallback(null, failureCallback);

    listenableFutureRegistry.fail(mock(ResponseFailEntry.class));
  }

  @Test
  void testSuccessWithCallbackFailAfter() {
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl();

    listenableFutureRegistry.fail(mock(ResponseFailEntry.class));

    listenableFutureRegistry.addCallback(null, failureCallback);
  }

  @Test
  void testSuccessWithoutCallbacksBefore() {
    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl();

    listenableFutureRegistry.addCallback(null, null);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));

    assertThat(listenableFutureRegistry.getState(), is(State.SUCCESS));
    assertThat(listenableFutureRegistry.getSuccessResult(), notNullValue());
    assertThat(listenableFutureRegistry.getFailureResult(), nullValue());
  }

  @Test
  void testSuccessWithoutCallbacksAfter() {
    final ListenableFutureImpl listenableFutureRegistry = new ListenableFutureImpl();

    listenableFutureRegistry.fail(mock(ResponseFailEntry.class));

    listenableFutureRegistry.addCallback(null, null);

    assertThat(listenableFutureRegistry.getState(), is(State.FAILURE));
    assertThat(listenableFutureRegistry.getFailureResult(), notNullValue());
    assertThat(listenableFutureRegistry.getSuccessResult(), nullValue());
  }

}
// @formatter:on
