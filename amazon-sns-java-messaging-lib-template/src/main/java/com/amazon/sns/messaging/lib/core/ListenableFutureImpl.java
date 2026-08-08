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

import static br.com.fluentvalidator.predicate.LogicalPredicate.not;
import static java.util.function.Function.identity;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

import lombok.Getter;

// @formatter:off
/**
 * Default implementation of {@link ListenableFuture}. Supports state tracking
 * (NEW, SUCCESS, FAILURE), thread-safe callback registration and notification,
 * and blocking retrieval of the result via {@link #get()} /
 * {@link #get(Duration)}.
 * <p>
 * All state transitions and reads are guarded by {@link #mutex}. Completion
 * ({@link #success(ResponseSuccessEntry)} or {@link #fail(ResponseFailEntry)})
 * both notifies any registered callbacks synchronously and wakes up any thread
 * blocked in {@link #get()} / {@link #get(Duration)} via
 * {@link Object#notifyAll()}.
 */
class ListenableFutureImpl implements ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> {

  /**
   * Runs registered callbacks off of the calling (consumer) thread; see class
   * Javadoc.
   */
  private final Executor callbackExecutor;

  /**
   * Backing future. Completed with a success result, or exceptionally with a
   * {@link FailureSignal}.
   */
  private final CompletableFuture<ResponseSuccessEntry> delegate = new CompletableFuture<>();

  /**
   * Creates a new future whose callbacks are dispatched on the given executor.
   *
   * @param callbackExecutor the executor used to run success/failure callbacks;
   *                         must not be one of the library's own publish/consumer
   *                         executors, to avoid starving them (see class Javadoc)
   */
  ListenableFutureImpl(final Executor callbackExecutor) {
    this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor cannot be null");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addCallback(final Consumer<? super ResponseSuccessEntry> successCallback, final Consumer<? super ResponseFailEntry> failureCallback) {
    final Consumer<? super ResponseSuccessEntry> success = Optional.ofNullable(successCallback).orElse(identity()::apply);
    final Consumer<? super ResponseFailEntry> failure = Optional.ofNullable(failureCallback).orElse(identity()::apply);

    delegate.whenCompleteAsync((result, throwable) -> {
      if (Objects.isNull(throwable)) {
        success.accept(result);
      } else {
        failure.accept(unwrap(throwable));
      }
    }, callbackExecutor);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void success(final ResponseSuccessEntry entry) {
    if (not(delegate::complete).test(entry)) {
      throw new IllegalStateException("ListenableFuture already completed.");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void fail(final ResponseFailEntry entry) {
    if (not(delegate::completeExceptionally).test(new FailureSignal(entry))) {
      throw new IllegalStateException("ListenableFuture already completed.");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ResponseSuccessEntry get() throws InterruptedException, ExecutionException {
    try {
      return delegate.get();
    } catch (final ExecutionException ex) {
      throw new ExecutionException(unwrapCause(ex.getCause()));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ResponseSuccessEntry get(final Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
    Objects.requireNonNull(timeout, "timeout cannot be null");

    try {
      return delegate.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (final ExecutionException ex) {
      throw new ExecutionException(unwrapCause(ex.getCause()));
    }
  }

  /**
   * Unwraps a {@link FailureSignal} into the {@link ResponseFailEntry} it
   * carries.
   *
   * @param throwable the throwable passed to
   *                  {@link CompletableFuture#whenCompleteAsync}; either a
   *                  {@link FailureSignal} directly, or (in composed/chained
   *                  usages) a {@link CompletionException} wrapping one
   * @return the original failure result
   */
  private static ResponseFailEntry unwrap(final Throwable throwable) {
    final Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
    return FailureSignal.class.cast(cause).getEntry();
  }

  /**
   * Returns the cause that {@link #get()} / {@link #get(Duration)} should report:
   * the original {@link Throwable} carried by the {@link ResponseFailEntry} if
   * present, or the entry itself (via {@link FailureSignal}) as a fallback.
   *
   * @param cause the cause of the {@link ExecutionException} thrown by the
   *              backing future, expected to be a {@link FailureSignal}
   * @return the throwable to expose as the {@link ExecutionException}'s cause
   */
  private static Throwable unwrapCause(final Throwable cause) {
    final ResponseFailEntry entry = FailureSignal.class.cast(cause).getEntry();
    return Optional.ofNullable(entry.getThrowable()).orElseGet(() -> new IllegalStateException(entry.getMessage()));
  }

  /**
   * Wraps a {@link ResponseFailEntry} so it can be used to complete the backing
   * {@link CompletableFuture} exceptionally via
   * {@link CompletableFuture#completeExceptionally(Throwable)}.
   */
  private static final class FailureSignal extends RuntimeException {

    private static final long serialVersionUID = -1790175325395257266L;

    @Getter
    private final transient ResponseFailEntry entry;

    FailureSignal(final ResponseFailEntry entry) {
      super(entry.getMessage());
      this.entry = entry;
    }

  }

}
// @formatter:on
