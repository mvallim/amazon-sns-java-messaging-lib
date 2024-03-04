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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

// @formatter:off
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractAmazonSnsProducer<E> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAmazonSnsProducer.class);

  private final ConcurrentMap<String, ListenableFutureRegistry> pendingRequests;

  private final BlockingQueue<RequestEntry<E>> topicRequests;

  private final ExecutorService executorService;

  @SneakyThrows
  public ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> send(final RequestEntry<E> requestEntry) {
    return CompletableFuture.supplyAsync(() -> enqueueRequest(requestEntry), executorService).get();
  }

  @SneakyThrows
  public void shutdown() {
    LOGGER.warn("Shutdown producer {}", getClass().getSimpleName());

    executorService.shutdown();
    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
      LOGGER.warn("Executor service did not terminate in the specified time.");
      final List<Runnable> droppedTasks = executorService.shutdownNow();
      LOGGER.warn("Executor service was abruptly shut down. {} tasks will not be executed.", droppedTasks.size());
    }
  }

  @SneakyThrows
  private ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> enqueueRequest(final RequestEntry<E> requestEntry) {
    final ListenableFutureRegistry trackPendingRequest = new ListenableFutureRegistry();
    pendingRequests.put(requestEntry.getId(), trackPendingRequest);
    topicRequests.put(requestEntry);
    return trackPendingRequest;
  }

}
// @formatter:on
