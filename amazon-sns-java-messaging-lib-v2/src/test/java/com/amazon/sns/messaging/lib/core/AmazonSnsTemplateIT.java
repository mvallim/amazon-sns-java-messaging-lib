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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;

import lombok.SneakyThrows;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;

// @formatter:off
@Testcontainers
@SuppressWarnings("resource")
class AmazonSnsTemplateIT {

  @Container
  static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
    .withEnv("LOCALSTACK_HOST", "localhost:4566")
    .withReuse(true)
    .withServices(Service.SNS);

  private static SnsClient snsClient;

  private static String standardTopicArn;

  private static String fifoTopicArn;

  private AmazonSnsTemplate<Object> snsTemplate;

  @BeforeAll
  static void setupClient() {
    snsClient = SnsClient.builder()
      .endpointOverride(URI.create(localstack.getEndpoint().toString()))
      .region(Region.of(localstack.getRegion()))
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
      .build();

    standardTopicArn = snsClient.createTopic(CreateTopicRequest.builder()
      .name("it-standard-topic")
      .build())
      .topicArn();

    final Map<String, String> fifoAttrs = new HashMap<>();
    fifoAttrs.put("FifoTopic", "true");
    fifoAttrs.put("ContentBasedDeduplication", "true");

    fifoTopicArn = snsClient.createTopic(CreateTopicRequest.builder()
      .name("it-fifo-topic.fifo")
      .attributes(fifoAttrs)
      .build())
      .topicArn();
  }

  @AfterAll
  static void tearDownClient() {
    if (Objects.nonNull(snsClient)) {
      snsClient.close();
    }

    if (Objects.nonNull(localstack)) {
      localstack.close();
    }
  }

  private void createTemplate(final boolean fifo, final String topicArn) {
    createTemplate(fifo, topicArn, 200L, 10, 5);
  }

  private void createTemplate(final boolean fifo, final String topicArn, final long linger, final int maxBatchSize, final int maximumPoolSize) {
    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(fifo)
      .linger(linger)
      .maxBatchSize(maxBatchSize)
      .maximumPoolSize(maximumPoolSize)
      .topicArn(topicArn)
      .build();

    snsTemplate = new AmazonSnsTemplate<>(snsClient, topicProperty);
  }

  @AfterEach
  void tearDown() {
    if (snsTemplate != null) {
      snsTemplate.shutdown();
    }
  }

  @SneakyThrows
  private void countDownLeach(final Integer count, final Consumer<CountDownLatch> consumer) {
    final CountDownLatch countDownLatch = new CountDownLatch(count);

    consumer.accept(countDownLatch);

    countDownLatch.await();
  }

  @Test
  void testSendSingleMessage() {
    countDownLeach(1, countDownLatch -> {
      createTemplate(false, standardTopicArn);

      final String id = UUID.randomUUID().toString();

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = snsTemplate.send(
          RequestEntry.builder()
          .withId(id)
          .withValue(Collections.singletonMap("key", "value"))
          .build());

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), is(id));
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      });
    });
  }

  @Test
  void testSendMultipleMessages() {
    final int count = 100;

    countDownLeach(count, countDownLatch -> {
      createTemplate(false, standardTopicArn);
      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      IntStream.range(0, count).forEach(i -> {
        futures.add(snsTemplate.send(RequestEntry.builder()
          .withId(UUID.randomUUID().toString())
          .withSubject("test-multi")
          .withValue(Collections.singletonMap("index", i))
          .build()));
      });

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      }));
    });
  }

  @Test
  void testSendMessageWithAttributes() {
    countDownLeach(1, countDownLatch -> {
      createTemplate(false, standardTopicArn);

      final String id = UUID.randomUUID().toString();

      final Map<String, Object> headers = new HashMap<>();
      headers.put("contentType", "text/plain");
      headers.put("priority", "high");

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = snsTemplate.send(
        RequestEntry.builder()
          .withId(id)
          .withValue("test-with-attributes")
          .withMessageHeaders(headers)
          .build());

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), is(id));
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      });
    });
  }

  @Test
  void testSendToFifoTopic() {
    countDownLeach(1, countDownLatch -> {
      createTemplate(true, fifoTopicArn);

      final String id = UUID.randomUUID().toString();
      final String groupId = UUID.randomUUID().toString();

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = snsTemplate.send(
        RequestEntry.builder()
          .withId(id)
          .withGroupId(groupId)
          .withValue(Collections.singletonMap("fifo", true))
          .build());

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), is(id));
        assertThat(result.getMessageId(), notNullValue());
        assertThat(result.getSequenceNumber(), notNullValue());
        countDownLatch.countDown();
      });
    });
  }

  @Test
  void testSendMessagesExceedingBatchSize() {
    countDownLeach(1, countDownLatch -> {
      createTemplate(false, standardTopicArn, 200L, 10, 5);

      final int count = 25;
      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      IntStream.range(0, count).forEach(i -> {
        futures.add(snsTemplate.send(RequestEntry.builder()
          .withId(UUID.randomUUID().toString())
          .withValue(Collections.singletonMap("index", i))
          .build()));
      });

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      }));
    });
  }

  @Test
  void testSendMessagesWithLinger() throws Exception {
    createTemplate(false, standardTopicArn, 2000L, 100, 5);

    final String id = UUID.randomUUID().toString();

    snsTemplate.send(RequestEntry.builder()
      .withId(id)
      .withValue("linger-test")
      .build());

    assertThrows(TimeoutException.class, () -> snsTemplate.await().get(500, TimeUnit.MILLISECONDS));
    snsTemplate.await().get(5000, TimeUnit.MILLISECONDS);
  }

  @Test
  void testSendLargeMessage() {
    countDownLeach(1, countDownLatch -> {
      createTemplate(false, standardTopicArn, 500L, 10, 5);

      final String id = UUID.randomUUID().toString();
      final String largePayload = repeat('A', 200_000);

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = snsTemplate.send(
        RequestEntry.builder()
          .withId(id)
          .withValue(largePayload)
          .build());

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), is(id));
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      });
    });
  }

  @Test
  void testSendMessageExceedingMaxSize() {
    countDownLeach(1, countDownLatch -> {
      createTemplate(false, standardTopicArn, 500L, 10, 5);

      final String id = UUID.randomUUID().toString();

      final String largePayload = repeat('B', 300_000);

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = snsTemplate.send(
        RequestEntry.builder()
          .withId(id)
          .withValue(largePayload)
          .build());

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      final ResponseFailEntry[] failure = new ResponseFailEntry[1];

      future.addCallback(null, failureResult -> {
        failure[0] = failureResult;
        countDownLatch.countDown();
      });

      assertThat(failure[0], notNullValue());
      assertThat(failure[0].getId(), is(id));
      assertThat(failure[0].getMessage(), containsString("maximum allowed message size exceeding 256KB"));
    });
  }

  @Test
  void testShutdownDrainsPendingMessages() {
    countDownLeach(10, countDownLatch -> {
      createTemplate(false, standardTopicArn, 200L, 10, 5);

      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      IntStream.range(0, 10).forEach(i -> {
        futures.add(snsTemplate.send(RequestEntry.builder()
          .withId(UUID.randomUUID().toString())
          .withValue("shutdown-test-" + i)
          .build()));
      });

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      }));
    });
  }

  @Test
  void testTemplateLifecycle() {
    countDownLeach(1, countDownLatch -> {
      createTemplate(false, standardTopicArn, 200L, 10, 5);

      final String id = UUID.randomUUID().toString();

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = snsTemplate.send(
        RequestEntry.builder()
          .withId(id)
          .withValue("lifecycle-test")
          .build());

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), is(id));
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      });
    });
  }

  @Test
  void testSendFifoMessagesWithOrdering() {
    countDownLeach(10, countDownLatch -> {
      createTemplate(true, fifoTopicArn, 200L, 10, 5);

      final String groupId = UUID.randomUUID().toString();

      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      IntStream.range(0, 10).forEach(i -> {
        futures.add(snsTemplate.send(RequestEntry.builder()
          .withId(UUID.randomUUID().toString())
          .withGroupId(groupId)
          .withValue(Collections.singletonMap("order", i))
          .build()));
      });

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        assertThat(result.getSequenceNumber(), notNullValue());
        countDownLatch.countDown();
      }));
    });
  }

  @Test
  void testSendFifoMessageWithDeduplication() {
    countDownLeach(1, countDownLatch -> {
      createTemplate(true, fifoTopicArn, 200L, 10, 5);

      final String id = UUID.randomUUID().toString();
      final String groupId = UUID.randomUUID().toString();
      final String dedupId = UUID.randomUUID().toString();

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = snsTemplate.send(
        RequestEntry.builder()
          .withId(id)
          .withGroupId(groupId)
          .withDeduplicationId(dedupId)
          .withValue(Collections.singletonMap("dedup", true))
          .build());

      snsTemplate.await().thenRun(snsTemplate::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), is(id));
        assertThat(result.getMessageId(), notNullValue());
        assertThat(result.getSequenceNumber(), notNullValue());
        countDownLatch.countDown();
      });
    });
  }

  private String repeat(final char ch, final int count) {
    final StringBuilder sb = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      sb.append(ch);
    }
    return sb.toString();
  }

}
// @formatter:on
