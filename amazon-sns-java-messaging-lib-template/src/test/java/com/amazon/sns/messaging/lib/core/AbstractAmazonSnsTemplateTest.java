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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazon.sns.messaging.lib.metrics.BlockingQueueMetricsDecorator;
import com.amazon.sns.messaging.lib.metrics.ExecutorServiceMetricsDecorator;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// @formatter:off
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "rawtypes", "unchecked"})
class AbstractAmazonSnsTemplateTest {

  @Mock
  private AbstractAmazonSnsProducer<String> producerMock;

  @Mock
  private AbstractAmazonSnsConsumer<Object, Object, Object, String> consumerMock;

  private AbstractAmazonSnsTemplate<Object, Object, Object, String> template;

  @BeforeEach
  void setUp() {
    template = new AbstractAmazonSnsTemplate<Object, Object, Object, String>(producerMock, consumerMock) { };
  }

  @Test
  void testSendDelegatesToProducer() {
    final RequestEntry<String> requestEntry = RequestEntry.<String>builder().build();
    final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> expectedFuture = new ListenableFutureImpl();
    when(producerMock.send(requestEntry)).thenReturn(expectedFuture);

    final ListenableFuture<ResponseSuccessEntry, ResponseFailEntry> result = template.send(requestEntry);

    assertThat(result, is(equalTo(expectedFuture)));
    verify(producerMock).send(requestEntry);
  }

  @Test
  void testShutdownDelegatesToProducer() {
    template.shutdown();
    verify(producerMock).shutdown();
  }

  @Test
  void testShutdownDelegatesToConsumer() {
    template.shutdown();
    verify(consumerMock).shutdown();
  }

  @Test
  void testAwaitDelegatesToConsumer() {
    final CompletableFuture<Void> expectedFuture = CompletableFuture.completedFuture(null);
    when(consumerMock.await()).thenReturn(expectedFuture);

    final CompletableFuture<Void> result = template.await();

    assertThat(result, is(equalTo(expectedFuture)));
    verify(consumerMock).await();
  }

  @Test
  void testGetExecutorServiceReturnsSingleThreadPoolForFifoTopic() {
    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(true)
      .topicArn("arn:aws:sns:us-east-2:000000000000:topic.fifo")
      .maximumPoolSize(10)
      .maxBatchSize(10)
      .build();

    final ExecutorService executorService = AbstractAmazonSnsTemplate.getExecutorService(topicProperty, new SimpleMeterRegistry());

    assertThat(executorService, is(notNullValue()));
    assertThat(executorService, is(instanceOf(ExecutorServiceMetricsDecorator.class)));
    executorService.shutdownNow();
  }

  @Test
  void testGetExecutorServiceReturnsMultiThreadPoolForNonFifoTopic() {
    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(false)
      .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
      .maximumPoolSize(5)
      .maxBatchSize(10)
      .build();

    final ExecutorService executorService = AbstractAmazonSnsTemplate.getExecutorService(topicProperty, new SimpleMeterRegistry());

    assertThat(executorService, is(notNullValue()));
    assertThat(executorService, is(instanceOf(ExecutorServiceMetricsDecorator.class)));
    executorService.shutdownNow();
  }

  @Test
  void testGetExecutorServiceWithNullMeterRegistryDoesNotThrow() {
    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(false)
      .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
      .maximumPoolSize(4)
      .maxBatchSize(10)
      .build();

    final ExecutorService executorService = AbstractAmazonSnsTemplate.getExecutorService(topicProperty, null);

    assertThat(executorService, is(notNullValue()));
    executorService.shutdownNow();
  }

  @Test
  void testBuilderThrowsWhenAmazonSnsClientIsNull() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    assertThrows(NullPointerException.class, () -> new AbstractAmazonSnsTemplate.Builder<>(builder -> null, null, topicProperty));
  }

  @Test
  void testBuilderThrowsWhenTopicPropertyIsNull() {
    assertThrows(NullPointerException.class, () -> new AbstractAmazonSnsTemplate.Builder<>(builder -> null, new Object(), null));
  }

  @Test
  void testBuilderThrowsWhenConstructorIsNull() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    assertThrows(NullPointerException.class, () -> new AbstractAmazonSnsTemplate.Builder<>(null, new Object(), topicProperty));
  }

  @Test
  void testBuilderPendingRequestsThrowsWhenNull() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    assertThrows(NullPointerException.class, () -> builder.pendingRequests(null));
  }

  @Test
  void testBuilderTopicRequestsThrowsWhenNull() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    assertThrows(NullPointerException.class, () -> builder.topicRequests(null));
  }

  @Test
  void testBuilderObjectMapperThrowsWhenNull() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    assertThrows(NullPointerException.class, () -> builder.objectMapper(null));
  }

  @Test
  void testBuilderPublishDecoratorThrowsWhenNull() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    assertThrows(NullPointerException.class, () -> builder.publishDecorator(null));
  }

  @Test
  void testBuilderMeterRegistryThrowsWhenNull() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    assertThrows(NullPointerException.class, () -> builder.meterRegistry(null));
  }

  @Test
  void testBuilderStoresAmazonSnsClient() {
    final Object client = new Object();
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, client, topicProperty);

    assertThat(builder.getAmazonSnsClient(), is(equalTo(client)));
  }

  @Test
  void testBuilderStoresTopicProperty() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    assertThat(builder.getTopicProperty(), is(equalTo(topicProperty)));
  }

  @Test
  void testBuilderDefaultPendingRequestsIsConcurrentHashMap() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    assertThat(builder.getPendingRequests(), is(instanceOf(ConcurrentHashMap.class)));
  }

  @Test
  void testBuilderDefaultObjectMapperIsNotNull() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    assertThat(builder.getObjectMapper(), is(notNullValue()));
    assertThat(builder.getObjectMapper(), is(instanceOf(ObjectMapper.class)));
  }

  @Test
  void testBuilderDefaultMeterRegistryIsSimpleMeterRegistry() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    assertThat(builder.getMeterRegistry(), is(instanceOf(SimpleMeterRegistry.class)));
  }

  @Test
  void testBuilderPendingRequestsReturnsSelf() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);
    final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> map = new ConcurrentHashMap<>();

    final AbstractAmazonSnsTemplate.Builder<?, ?, ?, ?, ?> result = builder.pendingRequests(map);

    assertThat(result, is(equalTo(builder)));
  }

  @Test
  void testBuilderTopicRequestsReturnsSelf() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);
    final BlockingQueue<RequestEntry<String>> queue = new LinkedBlockingDeque<>();

    final AbstractAmazonSnsTemplate.Builder<?, ?, ?, ?, ?> result = builder.topicRequests(queue);

    assertThat(result, is(equalTo(builder)));
  }

  @Test
  void testBuilderObjectMapperReturnsSelf() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    final AbstractAmazonSnsTemplate.Builder<?, ?, ?, ?, ?> result = builder.objectMapper(new ObjectMapper());

    assertThat(result, is(equalTo(builder)));
  }

  @Test
  void testBuilderPublishDecoratorReturnsSelf() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    final AbstractAmazonSnsTemplate.Builder<?, ?, ?, ?, ?> result = builder.publishDecorator(UnaryOperator.identity());

    assertThat(result, is(equalTo(builder)));
  }

  @Test
  void testBuilderMeterRegistryReturnsSelf() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);

    final AbstractAmazonSnsTemplate.Builder<?, ?, ?, ?, ?> result = builder.meterRegistry(new SimpleMeterRegistry());

    assertThat(result, is(equalTo(builder)));
  }

  @Test
  void testBuilderBuildWrapsTopicRequestsWithMetricsDecorator() {
    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(false)
      .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
      .maximumPoolSize(4)
      .maxBatchSize(10)
      .build();

    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> {
      assertThat(b.getTopicRequests(), is(instanceOf(BlockingQueueMetricsDecorator.class)));
      return null;
    }, new Object(), topicProperty);

    builder.build();
  }

  @Test
  void testBuilderBuildCreatesDefaultTopicRequestsWhenNotProvided() {
    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(false)
      .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
      .maximumPoolSize(4)
      .maxBatchSize(10)
      .build();

    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> {
      assertThat(b.getTopicRequests(), is(notNullValue()));
      return null;
    }, new Object(), topicProperty);

    builder.build();
  }

  @Test
  void testBuilderBuildUsesProvidedTopicRequests() {
    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(false)
      .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
      .maximumPoolSize(4)
      .maxBatchSize(10)
      .build();

    final BlockingQueue<RequestEntry<String>> customQueue = new LinkedBlockingDeque<>();

    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> {
      assertThat(b.getTopicRequests(), is(instanceOf(BlockingQueueMetricsDecorator.class)));
      return null;
    }, new Object(), topicProperty);

    builder.topicRequests(customQueue).build();
  }

  @Test
  void testBuilderBuildInvokesConstructorFunction() {
    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(false)
      .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
      .maximumPoolSize(4)
      .maxBatchSize(10)
      .build();

    final AbstractAmazonSnsTemplate sentinel = new AbstractAmazonSnsTemplate(producerMock, consumerMock) { };

    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> sentinel, new Object(), topicProperty);

    final Object result = builder.build();

    assertThat(result, is(equalTo(sentinel)));
  }

  @Test
  void testBuilderSetsPendingRequests() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final ConcurrentMap<String, ListenableFuture<ResponseSuccessEntry, ResponseFailEntry>> customMap = new ConcurrentHashMap<>();

    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);
    builder.pendingRequests(customMap);

    assertThat(builder.getPendingRequests(), is(equalTo(customMap)));
  }

  @Test
  void testBuilderSetsObjectMapper() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final ObjectMapper customMapper = new ObjectMapper();

    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);
    builder.objectMapper(customMapper);

    assertThat(builder.getObjectMapper(), is(equalTo(customMapper)));
  }

  @Test
  void testBuilderSetsMeterRegistry() {
    final TopicProperty topicProperty = mock(TopicProperty.class);
    final MeterRegistry customRegistry = new SimpleMeterRegistry();

    final AbstractAmazonSnsTemplate.Builder<Object, Object, Object, String, ?> builder = new AbstractAmazonSnsTemplate.Builder<>(b -> null, new Object(), topicProperty);
    builder.meterRegistry(customRegistry);

    assertThat(builder.getMeterRegistry(), is(equalTo(customRegistry)));
  }
}
