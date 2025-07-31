/*
 * Copyright 2023 the original author or authors.
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

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

import org.apache.commons.collections4.CollectionUtils;

import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

import lombok.AccessLevel;
import lombok.Getter;

// @formatter:off
class ListenableFutureImpl implements ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> {

  private final Object mutex = new Object();

  @Getter(value = AccessLevel.PACKAGE)
  private State state = State.NEW;

  @Getter(value = AccessLevel.PACKAGE)
  private ResponseSuccessEntry successResult;

  @Getter(value = AccessLevel.PACKAGE)
  private ResponseFailEntry failureResult;

  private final Queue<Consumer<? super ResponseSuccessEntry>> successCallback = new LinkedList<>();

  private final Queue<Consumer<? super ResponseFailEntry>> failureCallback = new LinkedList<>();

  @Override
  public void addCallback(final Consumer<? super ResponseSuccessEntry> successCallback, final Consumer<? super ResponseFailEntry> failureCallback) {
    synchronized (mutex) {
      final Consumer<? super ResponseSuccessEntry> success = Objects.nonNull(successCallback) ? successCallback : result -> { };
      final Consumer<? super ResponseFailEntry> failure = Objects.nonNull(failureCallback) ? failureCallback : result -> { };

      switch (state) {
        case NEW:
          this.successCallback.add(success);
          this.failureCallback.add(failure);
          break;
        case SUCCESS:
          notifySuccess(success);
          break;
        case FAILURE:
          notifyFailure(failure);
          break;
      }
    }
  }

  @Override
  public void success(final ResponseSuccessEntry entry) {
    synchronized (mutex) {
      state = State.SUCCESS;
      successResult = entry;

      while (CollectionUtils.isNotEmpty(successCallback)) {
        notifySuccess(successCallback.poll());
      }
    }
  }

  @Override
  public void fail(final ResponseFailEntry entry) {
    synchronized (mutex) {
      state = State.FAILURE;
      failureResult = entry;

      while (CollectionUtils.isNotEmpty(failureCallback)) {
        notifyFailure(failureCallback.poll());
      }
    }
  }

  private void notifySuccess(final Consumer<? super ResponseSuccessEntry> callback) {
    callback.accept(successResult);
  }

  private void notifyFailure(final Consumer<? super ResponseFailEntry> callback) {
    callback.accept(failureResult);
  }

  enum State {
    NEW, SUCCESS, FAILURE
  }

}
// @formatter:on
