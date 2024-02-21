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

package com.amazon.sns.messaging.lib.core.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazon.sns.messaging.lib.core.AmazonSnsTemplate;
import com.amazon.sns.messaging.lib.core.helper.ConsumerHelper;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.BatchResultErrorEntry;
import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchResult;
import com.amazonaws.services.sns.model.PublishBatchResultEntry;

// @formatter:off
@ExtendWith(MockitoExtension.class)
class AmazonSnsProducerAsyncTest {

  private AmazonSnsTemplate<Object> snsTemplate;

  @Mock
  private AmazonSNS amazonSNS;

  @BeforeEach
  public void before() throws Exception {
    final TopicProperty topicProperty = TopicProperty.builder()
      .fifo(false)
      .linger(50L)
      .maxBatchSize(10)
      .maximumPoolSize(10)
      .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
      .build();
    snsTemplate = new AmazonSnsTemplate<>(amazonSNS, topicProperty, new LinkedBlockingQueue<>(1024));
  }

  @Test
  void testSuccess() {
    final String id = UUID.randomUUID().toString();

    final PublishBatchResultEntry publishBatchResultEntry = new PublishBatchResultEntry();
    publishBatchResultEntry.setId(id);

    final PublishBatchResult publishBatchResult = new PublishBatchResult();
    publishBatchResult.getSuccessful().add(publishBatchResultEntry);

    when(amazonSNS.publishBatch(any())).thenReturn(publishBatchResult);

    snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().thenAccept(result -> {
      snsTemplate.shutdown();
      verify(amazonSNS, timeout(10000).times(1)).publishBatch(any());
    }).join();

  }

  @Test
  void testFailure() {
    final String id = UUID.randomUUID().toString();

    final BatchResultErrorEntry batchResultErrorEntry = new BatchResultErrorEntry();
    batchResultErrorEntry.setId(id);

    final PublishBatchResult publishBatchResult = new PublishBatchResult();
    publishBatchResult.getFailed().add(batchResultErrorEntry);

    when(amazonSNS.publishBatch(any())).thenReturn(publishBatchResult);

    snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(null, result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().thenAccept(result -> {
      verify(amazonSNS, timeout(10000).times(1)).publishBatch(any());
    }).join();

  }

  @Test
  void testSuccessMultipleEntry() {

    when(amazonSNS.publishBatch(any())).thenAnswer(invocation -> {
      final PublishBatchRequest request = invocation.getArgument(0, PublishBatchRequest.class);
      final List<PublishBatchResultEntry> resultEntries = request.getPublishBatchRequestEntries().stream()
        .map(entry -> new PublishBatchResultEntry().withId(entry.getId()))
        .collect(Collectors.toList());
      return new PublishBatchResult().withSuccessful(resultEntries);
    });

    final ConsumerHelper<ResponseSuccessEntry> successCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
    }));

    entries(30000).forEach(entry -> {
      snsTemplate.send(entry).addCallback(successCallback);
    });

    snsTemplate.await().thenAccept(result -> {
      verify(successCallback, timeout(300000).times(30000)).accept(any());
      verify(amazonSNS, atLeastOnce()).publishBatch(any());
    }).join();
  }

  @Test
  void testFailureMultipleEntry() {

    when(amazonSNS.publishBatch(any())).thenAnswer(invocation -> {
      final PublishBatchRequest request = invocation.getArgument(0, PublishBatchRequest.class);
      final List<BatchResultErrorEntry> resultEntries = request.getPublishBatchRequestEntries().stream()
        .map(entry -> new BatchResultErrorEntry().withId(entry.getId()))
        .collect(Collectors.toList());
      return new PublishBatchResult().withFailed(resultEntries);
    });

    final ConsumerHelper<ResponseFailEntry> failureCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
    }));

    entries(30000).forEach(entry -> {
      snsTemplate.send(entry).addCallback(null, failureCallback);
    });

    snsTemplate.await().thenAccept(result -> {
      verify(failureCallback, timeout(300000).times(30000)).accept(any());
      verify(amazonSNS, atLeastOnce()).publishBatch(any());
    }).join();
  }

  @Test
  void testFailRiseRuntimeException() {
    final String id = UUID.randomUUID().toString();

    when(amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenThrow(new RuntimeException());

    snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().thenAccept(result -> {
      verify(amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
    }).join();

  }

  @Test
  void testFailRiseAwsServiceException() {
    final String id = UUID.randomUUID().toString();

    when(amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenThrow(new AmazonServiceException("error"));

    snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().thenAccept(result -> {
      verify(amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
    }).join();

  }

  @Test
  void testSuccessBlockingSubmissionPolicy() {
    final TopicProperty topicProperty = TopicProperty.builder()
        .fifo(false)
        .linger(50L)
        .maxBatchSize(1)
        .maximumPoolSize(1)
        .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
        .build();

    final AmazonSnsTemplate<Object> snsTemplate = new AmazonSnsTemplate<>(amazonSNS, topicProperty);

    when(amazonSNS.publishBatch(any())).thenAnswer(invocation -> {
      while (true) {
        Thread.sleep(1);
      }
    });

    final ConsumerHelper<ResponseFailEntry> failureCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
    }));

    entries(2).forEach(entry -> {
      snsTemplate.send(entry).addCallback(null, failureCallback);;
    });

    verify(failureCallback, timeout(40000).times(1)).accept(any());
    verify(amazonSNS, atLeastOnce()).publishBatch(any());
  }

  private List<RequestEntry<Object>> entries(final int amount) {
    final LinkedList<RequestEntry<Object>> entries = new LinkedList<>();

    for (int i = 0; i < amount; i++) {
      entries.add(RequestEntry.builder()
        .withId(RandomStringUtils.randomAlphabetic(36))
        .withSubject("subject")
        .withGroupId(UUID.randomUUID().toString())
        .withDeduplicationId(UUID.randomUUID().toString())
        .build());
    }

    return entries;
  }

}
// @formatter:on
