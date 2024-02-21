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

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

// @formatter:off
class AmazonSnsThreadPoolExecutorTest {

  @Test
  void testSuccessCounters() {
    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = new AmazonSnsThreadPoolExecutor(10);

    assertThat(amazonSnsThreadPoolExecutor.getActiveTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getFailedTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getCorePoolSize(), is(0));
  }

  @Test
  void testSuccessSucceededTaskCount() throws InterruptedException {
    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = new AmazonSnsThreadPoolExecutor(10);

    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));

    for(int i = 0; i < 300; i++) {
      amazonSnsThreadPoolExecutor.execute(() -> {
        try {
          Thread.sleep(1);
        } catch (final InterruptedException e) {
          e.printStackTrace();
        }
      });
    }

    amazonSnsThreadPoolExecutor.shutdown();

    if (!amazonSnsThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      amazonSnsThreadPoolExecutor.shutdownNow();
    }

    assertThat(amazonSnsThreadPoolExecutor.getActiveTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(300));
    assertThat(amazonSnsThreadPoolExecutor.getFailedTaskCount(), is(0));
  }

  @Test
  void testSuccessFailedTaskCount() throws InterruptedException {
    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = new AmazonSnsThreadPoolExecutor(10);

    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));

    for(int i = 0; i < 300; i++) {
      amazonSnsThreadPoolExecutor.execute(() -> { throw new RuntimeException(); });
    }

    amazonSnsThreadPoolExecutor.shutdown();

    if (!amazonSnsThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      amazonSnsThreadPoolExecutor.shutdownNow();
    }

    assertThat(amazonSnsThreadPoolExecutor.getActiveTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getFailedTaskCount(), is(300));
  }

  @Test
  void testSuccessActiveTaskCount() throws InterruptedException {
    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = new AmazonSnsThreadPoolExecutor(10);

    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));

    for(int i = 0; i < 10; i++) {
      amazonSnsThreadPoolExecutor.execute(() -> {
        while(true) {
          try {
            Thread.sleep(1);
          } catch (final InterruptedException e) {
            e.printStackTrace();
          }
        }
      });
    }

    amazonSnsThreadPoolExecutor.shutdown();

    if (!amazonSnsThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      amazonSnsThreadPoolExecutor.shutdownNow();
    }

    assertThat(amazonSnsThreadPoolExecutor.getActiveTaskCount(), is(10));
    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getFailedTaskCount(), is(0));
  }

  @Test
  void testSuccessBlockingSubmissionPolicy() throws InterruptedException {
    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = new AmazonSnsThreadPoolExecutor(1);

    amazonSnsThreadPoolExecutor.execute(() -> {
      while(true) {
        try {
          Thread.sleep(1);
        } catch (final InterruptedException e) {
          e.printStackTrace();
        }
      }
    });

    catchThrowableOfType(() -> amazonSnsThreadPoolExecutor.execute(() -> { }), RejectedExecutionException.class);
  }

}
// @formatter:on
