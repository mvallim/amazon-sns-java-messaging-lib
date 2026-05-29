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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.amazon.sns.messaging.lib.concurrent.RingBufferBlockingQueue;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;

import lombok.SneakyThrows;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

// @formatter:off
@Testcontainers
@SuppressWarnings("resource")
class AmazonSnsTemplateIntegrationTest {

  @Container
  static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
    .withEnv("LOCALSTACK_HOST", "localhost:4566")
    .withEnv("SQS_ENDPOINT_STRATEGY", "dynamic")
    .withReuse(true)
    .withServices(Service.SNS, Service.SQS);

  private static SnsClient snsClient;

  private static SqsClient sqsClient;

  private static String standardTopicArn;

  private static String standardQueueUrl;

  private static String fifoTopicArn;

  private static String fifoQueueUrl;

  @BeforeAll
  static void setupClient() {
    snsClient = SnsClient.builder()
      .endpointOverride(URI.create(localstack.getEndpoint().toString()))
      .region(Region.of(localstack.getRegion()))
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
      .build();

    sqsClient = SqsClient.builder()
      .endpointOverride(localstack.getEndpoint())
      .region(Region.of(localstack.getRegion()))
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
      .build();

    standardTopicArn = snsClient.createTopic(request -> request.name("it-standard-topic")).topicArn();

    standardQueueUrl = sqsClient.createQueue(request -> request.queueName("it-standard-queue")).queueUrl();

    final Map<String, String> fifoTopicAttributes = new HashMap<>();
    fifoTopicAttributes.put("FifoTopic", "true");
    fifoTopicAttributes.put("ContentBasedDeduplication", "true");

    fifoTopicArn = snsClient.createTopic(request -> request
      .name("it-fifo-topic.fifo")
      .attributes(fifoTopicAttributes)).topicArn();

    final Map<QueueAttributeName, String> fifoQueueAttributes = new EnumMap<>(QueueAttributeName.class);
    fifoQueueAttributes.put(QueueAttributeName.FIFO_QUEUE, "true");
    fifoQueueAttributes.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true");

    fifoQueueUrl = sqsClient.createQueue(request -> request
      .queueName("it-fifo-queue.fifo")
      .attributes(fifoQueueAttributes)).queueUrl();

    snsClient.subscribe(request -> request
      .protocol("sqs")
      .topicArn(standardTopicArn)
      .attributes(Collections.singletonMap("RawMessageDelivery", "true"))
      .endpoint(sqsClient.getQueueAttributes(r -> r
        .queueUrl(standardQueueUrl)
        .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN))
    );

    snsClient.subscribe(request -> request
      .protocol("sqs")
      .topicArn(fifoTopicArn)
      .attributes(Collections.singletonMap("RawMessageDelivery", "true"))
      .endpoint(sqsClient.getQueueAttributes(r -> r
        .queueUrl(fifoQueueUrl)
        .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN))
    );
  }

  @AfterAll
  static void tearDownClient() {
    if (Objects.nonNull(snsClient)) {
      snsClient.close();
    }

    if (Objects.nonNull(sqsClient)) {
      sqsClient.close();
    }

    if (Objects.nonNull(localstack)) {
      localstack.close();
    }
  }

  @BeforeEach
  void before() {
    purgeQueue(standardQueueUrl);
    purgeQueue(fifoQueueUrl);
  }

  private AmazonSnsTemplate<Object> createTemplate(
      final String topicArn,
      final boolean fifo,
      final long linger,
      final int maxBatchSize,
      final int maxPoolSize) {

    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(fifo)
      .linger(linger)
      .maxBatchSize(maxBatchSize)
      .maximumPoolSize(maxPoolSize)
      .topicArn(topicArn)
      .build();

    return new AmazonSnsTemplate<>(snsClient, topicProperty, new RingBufferBlockingQueue<>(1024));
  }

  private void purgeQueue(final String queueUrl) {
    sqsClient.purgeQueue(request -> request.queueUrl(queueUrl));
  }

  private ReceiveMessageResponse receiveMessage(final String queueUrl, final Integer maxNumberOfMessages, final Integer waitTimeSeconds) {
    final ReceiveMessageResponse result = sqsClient.receiveMessage(request -> request
      .queueUrl(queueUrl)
      .maxNumberOfMessages(maxNumberOfMessages)
      .waitTimeSeconds(waitTimeSeconds)
      .attributeNames(QueueAttributeName.ALL)
      .messageAttributeNames("All"));

    result.messages().forEach(message -> sqsClient.deleteMessage(request -> request.queueUrl(queueUrl).receiptHandle(message.receiptHandle())));

    return result;
  }

  @SneakyThrows
  private void countDownLatch(final Integer count, final Consumer<CountDownLatch> consumer) {
    final CountDownLatch countDownLatch = new CountDownLatch(count);

    consumer.accept(countDownLatch);

    countDownLatch.await(1L, TimeUnit.MINUTES);
  }

  @Test
  void testSendSingleMessage() {
    final String messageBody = "hello-sqs-" + UUID.randomUUID();

    countDownLatch(1, countDownLatch -> {

      final AmazonSnsTemplate<Object> template = createTemplate(standardTopicArn, false, 100L, 10, 5);

      final String id = UUID.randomUUID().toString();

      final ListenableFuture<ResponseSuccessEntry,ResponseFailEntry> future = template.send(RequestEntry.builder()
        .withId(id)
        .withValue(messageBody)
        .build());

      template.await().thenRun(template::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), is(id));
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      });
    });

    final ReceiveMessageResponse result = receiveMessage(standardQueueUrl, 1, 5);

    assertThat(result.messages(), hasSize(1));

    final Message message = result.messages().get(0);
    assertThat(message.body(), is(messageBody));
    assertThat(message.messageAttributes().keySet(), hasSize(0));
  }

  @Test
  void testSendMultipleMessages() {
    final int messageCount = 500;

    countDownLatch(messageCount, countDownLatch -> {
      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      final AmazonSnsTemplate<Object> template = createTemplate(standardTopicArn, false, 50L, 10, 10);

      IntStream.range(0, messageCount).forEach(i -> {
        futures.add(
          template.send(RequestEntry.builder()
            .withId(UUID.randomUUID().toString())
            .withValue("msg-" + i + "-" + UUID.randomUUID())
            .build())
          );
      });

      template.await().thenRun(template::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      }));
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < messageCount) {
      messages.addAll(receiveMessage(standardQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(messageCount));

    messages.forEach(message -> {
      assertThat(message.body(), containsString("msg-"));
      assertThat(message.messageAttributes().keySet(), hasSize(0));
    });
  }

  @Test
  void testSendMessagesExceedingBatchSize() {
    final int messageCount = 25;

    countDownLatch(messageCount, countDownLatch -> {
      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      final AmazonSnsTemplate<Object> template = createTemplate(standardTopicArn, false, 50L, 10, 10);

      IntStream.range(0, messageCount).forEach(i -> {
        futures.add(template.send(RequestEntry.builder()
          .withId(UUID.randomUUID().toString())
          .withValue("batch-test-" + i)
          .build()));
      });

      template.await().thenRun(template::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      }));
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < messageCount) {
      messages.addAll(receiveMessage(standardQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(messageCount));

    messages.forEach(message -> {
      assertThat(message.body(), containsString("batch-test-"));
      assertThat(message.messageAttributes().keySet(), hasSize(0));
    });
  }


  @Test
  void testSendMessagesWithLinger() {
    final int messageCount = 20;

    countDownLatch(messageCount, countDownLatch -> {
      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      final AmazonSnsTemplate<Object> template = createTemplate(standardTopicArn, false, 200L, 10, 5);

      IntStream.range(0, messageCount).forEach(i -> {
        futures.add(template.send(RequestEntry.builder()
          .withId(UUID.randomUUID().toString())
          .withValue("linger-test-" + i)
          .build()));
      });

      template.await().thenRun(template::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      }));
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < messageCount) {
      messages.addAll(receiveMessage(standardQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(messageCount));

    messages.forEach(message -> {
      assertThat(message.body(), containsString("linger-test-"));
      assertThat(message.messageAttributes().keySet(), hasSize(0));
    });
  }

  @Test
  void testSendMessageWithgetMessageAttributes() {
    final String messageBody = "attr-test-" + UUID.randomUUID();

    countDownLatch(1, countDownLatch -> {
      final Map<String, Object> messageHeaders = new HashMap<>();
      messageHeaders.put("string-attr", "hello");
      messageHeaders.put("number-attr", 42);

      final AmazonSnsTemplate<Object> template = createTemplate(standardTopicArn, false, 100L, 10, 5);

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = template.send(RequestEntry.builder()
        .withId(UUID.randomUUID().toString())
        .withValue(messageBody)
        .withMessageHeaders(messageHeaders)
        .build());

      template.await().thenRun(template::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      });
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < 1) {
      messages.addAll(receiveMessage(standardQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(1));

    messages.forEach(message -> {
      assertThat(message.body(), is(messageBody));
      assertThat(message.messageAttributes().get("string-attr").stringValue(), is("hello"));
      assertThat(message.messageAttributes().get("number-attr").stringValue(), is("42"));
    });
  }

  @Test
  void testSendLargeMessage() {
    final String largeBody = RandomStringUtils.secure().nextAlphabetic(262_144);

    countDownLatch(1, countDownLatch -> {
      final AmazonSnsTemplate<Object> template = createTemplate(standardTopicArn, false, 200L, 5, 5);

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = template.send(RequestEntry.builder()
        .withId(UUID.randomUUID().toString())
        .withValue(largeBody)
        .build());

      template.await().thenRun(template::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      });
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < 1) {
      messages.addAll(receiveMessage(standardQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(1));

    messages.forEach(message -> {
      assertThat(message.body(), is(largeBody));
      assertThat(message.messageAttributes().keySet(), hasSize(0));
    });
  }

  @Test
  void testSendMessageExceedingMaxSize() {
    countDownLatch(1, countDownLatch -> {
      final String oversizedBody = RandomStringUtils.secure().nextAlphabetic((1024 * 256) + 1);

      final RequestEntry<Object> entry = RequestEntry.builder()
        .withId(UUID.randomUUID().toString())
        .withValue(oversizedBody)
        .build();

      final AmazonSnsTemplate<Object> template = createTemplate(standardTopicArn, false, 100L, 10, 5);

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = template.send(entry);

      template.await().thenRun(template::shutdown).join();

      future.addCallback(null, failureResult -> {
        assertThat(failureResult.getCode(), is("000"));
        assertThat(failureResult.getId(), is(entry.getId()));
        assertThat(failureResult.getMessage(), containsString("The maximum allowed message size exceeding 256KB (262,144 bytes)."));
        assertThat(failureResult.getSenderFault(), is(true));
        countDownLatch.countDown();
      });
    });

    final List<Message> messages = receiveMessage(standardQueueUrl, 10, 5).messages();

    assertThat(messages, hasSize(0));
  }

  @Test
  void testShutdownDrainsPendingMessages() {
    final int messageCount = 5;

    countDownLatch(messageCount, countDownLatch -> {
      final AmazonSnsTemplate<Object> template = createTemplate(standardTopicArn, false, 10_000L, 10, 5);

      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      IntStream.range(0, messageCount).forEach(i -> {
        futures.add(template.send(RequestEntry.builder()
          .withId(UUID.randomUUID().toString())
          .withValue("drain-test-" + i)
          .build()));
      });

      template.await().thenRun(template::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      }));
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < messageCount) {
      messages.addAll(receiveMessage(standardQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(messageCount));

    messages.forEach(message -> {
      assertThat(message.body(), containsString("drain-test-"));
      assertThat(message.messageAttributes().keySet(), hasSize(0));
    });
  }

  @Test
  void testTemplateLifecycle() {
    countDownLatch(1, countDownLatch -> {
      final AmazonSnsTemplate<Object> template = createTemplate(standardTopicArn, false, 100L, 10, 5);

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = template.send(RequestEntry.builder()
        .withId(UUID.randomUUID().toString())
        .withValue("lifecycle-" + UUID.randomUUID())
        .build());

      template.await().thenRun(template::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        countDownLatch.countDown();
      });
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < 1) {
      messages.addAll(receiveMessage(standardQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(1));

    messages.forEach(message -> {
      assertThat(message.body(), containsString("lifecycle-"));
      assertThat(message.messageAttributes().keySet(), hasSize(0));
    });
  }

  @Test
  void testSendSingleFifoMessage() {
    final String messageBody = "fifo-single-" + UUID.randomUUID();
    final String id = UUID.randomUUID().toString();
    final String groupId = id;

    countDownLatch(1, countDownLatch -> {
      final AmazonSnsTemplate<Object> template = createTemplate(fifoTopicArn, true, 100L, 10, 1);

      final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> future = template.send(RequestEntry.builder()
        .withId(id)
        .withValue(messageBody)
        .withGroupId(groupId)
        .build());

      template.await().thenRun(template::shutdown).join();

      future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), is(id));
        assertThat(result.getMessageId(), notNullValue());
        assertThat(result.getSequenceNumber(), notNullValue());
        countDownLatch.countDown();
      });
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < 1) {
      messages.addAll(receiveMessage(fifoQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(1));

    messages.forEach(message -> {
      assertThat(message.body(), containsString("fifo-single-"));
      assertThat(message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID), is(groupId));
      assertThat(message.messageAttributes().keySet(), hasSize(0));
    });
  }

  @Test
  void testSendFifoMessagesWithOrdering() {
    final int messageCount = 100;
    final String groupId = UUID.randomUUID().toString();

    countDownLatch(1, countDownLatch -> {
      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      final AmazonSnsTemplate<Object> template = createTemplate(fifoTopicArn, true, 50L, 10, 1);

      IntStream.range(0, messageCount).forEach(i -> {
        futures.add(template.send(RequestEntry.builder()
          .withId(UUID.randomUUID().toString())
          .withValue("ordered-" + i)
          .withGroupId(groupId)
          .build()));
      });

      template.await().thenRun(template::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        assertThat(result.getSequenceNumber(), notNullValue());
        countDownLatch.countDown();
      }));
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < messageCount) {
      messages.addAll(receiveMessage(fifoQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(messageCount));

    messages.forEach(message -> {
      assertThat(message.body(), containsString("ordered-"));
      assertThat(message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID), is(groupId));
      assertThat(message.messageAttributes().keySet(), hasSize(0));
    });
  }

  @Test
  void testSendFifoMessageWithDeduplication() {
    final String deduplicationId = UUID.randomUUID().toString();
    final String groupId = UUID.randomUUID().toString();
    final String messageBody = "dedup-test-" + UUID.randomUUID();

    countDownLatch(1, countDownLatch -> {
      final List<ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> futures = new ArrayList<>();

      final AmazonSnsTemplate<Object> template = createTemplate(fifoTopicArn, true, 100L, 10, 1);

      futures.add(template.send(RequestEntry.builder()
        .withId(UUID.randomUUID().toString())
        .withValue(messageBody)
        .withGroupId(groupId)
        .withDeduplicationId(deduplicationId)
        .build()));

      futures.add(template.send(RequestEntry.builder()
        .withId(UUID.randomUUID().toString())
        .withValue(messageBody + "-duplicate")
        .withGroupId(groupId)
        .withDeduplicationId(deduplicationId)
        .build()));

      template.await().thenRun(template::shutdown).join();

      futures.forEach(future -> future.addCallback(result -> {
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(result.getMessageId(), notNullValue());
        assertThat(result.getSequenceNumber(), notNullValue());
        countDownLatch.countDown();
      }));
    });

    final List<Message> messages = new LinkedList<>();

    while (messages.size() < 1) {
      messages.addAll(receiveMessage(fifoQueueUrl, 10, 5).messages());
    }

    assertThat(messages, hasSize(1));

    messages.forEach(message -> {
      assertThat(message.body(), is(messageBody));
      assertThat(message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID), is(groupId));
      assertThat(message.attributes().get(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID), is(deduplicationId));
      assertThat(message.messageAttributes().keySet(), hasSize(0));
    });
  }

}
// @formatter:on
