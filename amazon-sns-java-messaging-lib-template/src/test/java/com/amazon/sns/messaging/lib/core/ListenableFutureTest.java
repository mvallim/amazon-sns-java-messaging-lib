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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

@ExtendWith(MockitoExtension.class)
class ListenableFutureTest {

  private ListenableFutureImpl listenableFuture;

  @Mock(strictness = Strictness.LENIENT)
  private Consumer<ResponseSuccessEntry> successCallback;

  @Mock(strictness = Strictness.LENIENT)
  private Consumer<ResponseFailEntry> failureCallback;

  @BeforeEach
  void setUp() {
    listenableFuture = new ListenableFutureImpl();
  }

  @Test
  void testAddCallbackInvokesSuccessCallbackOnSuccess() {
    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);

    listenableFuture.addCallback(successCallback, failureCallback);
    listenableFuture.success(entry);

    verify(successCallback).accept(entry);
  }

  @Test
  void testAddCallbackInvokesFailureCallbackOnFail() {
    final ResponseFailEntry entry = mock(ResponseFailEntry.class);

    listenableFuture.addCallback(successCallback, failureCallback);
    listenableFuture.fail(entry);

    verify(failureCallback).accept(entry);
  }

  @Test
  void testAddCallbackWithSuccessOnlyInvokesSuccessCallbackOnSuccess() {
    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);

    listenableFuture.addCallback(successCallback);
    listenableFuture.success(entry);

    verify(successCallback).accept(entry);
  }

  @Test
  void testAddCallbackWithSuccessOnlyDoesNotThrowOnFail() {
    final ResponseFailEntry entry = mock(ResponseFailEntry.class);

    listenableFuture.addCallback(successCallback);

    assertDoesNotThrow(() -> listenableFuture.fail(entry));
  }

  @Test
  void testSuccessDoesNotInvokeFailureCallback() {
    final ResponseSuccessEntry entry = mock(ResponseSuccessEntry.class);

    listenableFuture.addCallback(successCallback, failureCallback);
    listenableFuture.success(entry);

    org.mockito.Mockito.verifyNoInteractions(failureCallback);
  }

  @Test
  void testFailDoesNotInvokeSuccessCallback() {
    final ResponseFailEntry entry = mock(ResponseFailEntry.class);

    listenableFuture.addCallback(successCallback, failureCallback);
    listenableFuture.fail(entry);

    org.mockito.Mockito.verifyNoInteractions(successCallback);
  }

  @Test
  void testAddCallbackDefaultFailureCallbackIsNoOp() {
    final boolean[] called = { false };
    final ResponseFailEntry entry = mock(ResponseFailEntry.class);

    listenableFuture.addCallback(successCallback -> called[0] = true);
    listenableFuture.fail(entry);

    assertThat(called[0], is(false));
  }

}