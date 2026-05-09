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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

class ListenableFutureTest {

  private ListenableFutureImpl listenableFuture;

  @BeforeEach
  void setUp() {
    listenableFuture = new ListenableFutureImpl();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testAddCallbackInvokesSuccessCallbackOnSuccess() {
    final Consumer<ResponseSuccessEntry> successCallback = mock(Consumer.class);
    final Consumer<ResponseFailEntry> failureCallback = mock(Consumer.class);
    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);

    listenableFuture.addCallback(successCallback, failureCallback);
    listenableFuture.success(entry);

    verify(successCallback).accept(entry);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testAddCallbackInvokesFailureCallbackOnFail() {
    final Consumer<ResponseSuccessEntry> successCallback = mock(Consumer.class);
    final Consumer<ResponseFailEntry> failureCallback = mock(Consumer.class);
    final ResponseFailEntry entry = mock(ResponseFailEntry.class);

    listenableFuture.addCallback(successCallback, failureCallback);
    listenableFuture.fail(entry);

    verify(failureCallback).accept(entry);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testAddCallbackWithSuccessOnlyInvokesSuccessCallbackOnSuccess() {
    final Consumer<ResponseSuccessEntry> successCallback = mock(Consumer.class);
    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);

    listenableFuture.addCallback(successCallback);
    listenableFuture.success(entry);

    verify(successCallback).accept(entry);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testAddCallbackWithSuccessOnlyDoesNotThrowOnFail() {
    final Consumer<ResponseSuccessEntry> successCallback = mock(Consumer.class);
    final ResponseFailEntry entry = mock(ResponseFailEntry.class);

    listenableFuture.addCallback(successCallback);

    org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> listenableFuture.fail(entry));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSuccessDoesNotInvokeFailureCallback() {
    final Consumer<ResponseSuccessEntry> successCallback = mock(Consumer.class);
    final Consumer<ResponseFailEntry> failureCallback = mock(Consumer.class);
    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);

    listenableFuture.addCallback(successCallback, failureCallback);
    listenableFuture.success(entry);

    org.mockito.Mockito.verifyNoInteractions(failureCallback);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testFailDoesNotInvokeSuccessCallback() {
    final Consumer<ResponseSuccessEntry> successCallback = mock(Consumer.class);
    final Consumer<ResponseFailEntry> failureCallback = mock(Consumer.class);
    final ResponseFailEntry entry = mock(ResponseFailEntry.class);

    listenableFuture.addCallback(successCallback, failureCallback);
    listenableFuture.fail(entry);

    org.mockito.Mockito.verifyNoInteractions(successCallback);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testAddCallbackDefaultFailureCallbackIsNoOp() {
    final boolean[] called = { false };
    final Consumer<ResponseSuccessEntry> successCallback = result -> called[0] = true;
    final ResponseFailEntry entry = mock(ResponseFailEntry.class);

    listenableFuture.addCallback(successCallback);
    listenableFuture.fail(entry);

    assertThat(called[0], is(false));
  }

}