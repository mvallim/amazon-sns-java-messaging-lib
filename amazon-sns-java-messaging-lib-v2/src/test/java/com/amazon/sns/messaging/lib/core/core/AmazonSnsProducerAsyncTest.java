/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.amazon.sns.messaging.lib.core.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.amazon.sns.messaging.lib.core.AmazonSnsTemplate;
import com.amazon.sns.messaging.lib.core.helper.ConsumerHelper;
import com.amazon.sns.messaging.lib.model.RequestEntry;
import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.lib.model.TopicProperty;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;
import software.amazon.awssdk.services.sns.model.PublishBatchResultEntry;

// @formatter:off
@RunWith(MockitoJUnitRunner.class)
public class AmazonSnsProducerAsyncTest {

  private AmazonSnsTemplate<Object> snsTemplate;

  @Mock
  private SnsClient amazonSNS;

  @Mock
  private TopicProperty topicProperty;

  @Before
  public void before() throws Exception {
    when(this.topicProperty.isFifo()).thenReturn(true);
    when(this.topicProperty.getTopicArn()).thenReturn("arn:aws:sns:us-east-2:000000000000:topic");
    when(this.topicProperty.getMaximumPoolSize()).thenReturn(10);
    when(this.topicProperty.getLinger()).thenReturn(50L);
    when(this.topicProperty.getMaxBatchSize()).thenReturn(10);
    this.snsTemplate = new AmazonSnsTemplate<>(this.amazonSNS, this.topicProperty, new LinkedBlockingQueue<>(1024));
  }

  @Test
  public void testSuccess() {
    final String id = UUID.randomUUID().toString();

    final PublishBatchResultEntry publishBatchResultEntry = PublishBatchResultEntry.builder().id(id).build();

    final PublishBatchResponse publishBatchResult = PublishBatchResponse.builder()
      .successful(publishBatchResultEntry)
      .build();

    when(this.amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenReturn(publishBatchResult);

    this.snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    this.snsTemplate.await().thenAccept(result -> {
      verify(this.amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
    }).join();

  }

  @Test
  public void testFailure() {
    final String id = UUID.randomUUID().toString();

    final BatchResultErrorEntry batchResultErrorEntry = BatchResultErrorEntry.builder().id(id).build();

    final PublishBatchResponse publishBatchResult = PublishBatchResponse.builder()
      .failed(batchResultErrorEntry)
      .build();

    when(this.amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenReturn(publishBatchResult);

    this.snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(null, result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    this.snsTemplate.await().thenAccept(result -> {
      verify(this.amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
    }).join();

  }

  @Test
  public void testSuccessMultipleEntry() {

    when(this.amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenAnswer(invocation -> {
      final PublishBatchRequest request = invocation.getArgumentAt(0, PublishBatchRequest.class);
      final List<PublishBatchResultEntry> resultEntries = request.publishBatchRequestEntries().stream()
        .map(entry -> PublishBatchResultEntry.builder().id(entry.id()).build())
        .collect(Collectors.toList());
      return PublishBatchResponse.builder().successful(resultEntries).build();
    });

    final ConsumerHelper<ResponseSuccessEntry> successCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
    }));

    CompletableFuture.runAsync(() -> {
      entries(10000).forEach(entry -> {
        this.snsTemplate.send(entry).addCallback(successCallback);
      });
    });

    CompletableFuture.runAsync(() -> {
      entries(10000).forEach(entry -> {
        this.snsTemplate.send(entry).addCallback(successCallback);
      });
    });

    CompletableFuture.runAsync(() -> {
      entries(10000).forEach(entry -> {
        this.snsTemplate.send(entry).addCallback(successCallback);
      });
    });

    this.snsTemplate.await().thenAccept(result -> {
      verify(successCallback, timeout(300000).times(30000)).accept(any());
      verify(this.amazonSNS, atLeastOnce()).publishBatch(any(PublishBatchRequest.class));
    }).join();
  }

  @Test
  public void testFailureMultipleEntry() {

    when(this.amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenAnswer(invocation -> {
      final PublishBatchRequest request = invocation.getArgumentAt(0, PublishBatchRequest.class);
      final List<BatchResultErrorEntry> resultEntries = request.publishBatchRequestEntries().stream()
        .map(entry -> BatchResultErrorEntry.builder().id(entry.id()).build())
        .collect(Collectors.toList());
      return PublishBatchResponse.builder().failed(resultEntries).build();
    });

    final ConsumerHelper<ResponseFailEntry> failureCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
    }));

    CompletableFuture.runAsync(() -> {
      entries(10000).forEach(entry -> {
        this.snsTemplate.send(entry).addCallback(null, failureCallback);
      });
    });

    CompletableFuture.runAsync(() -> {
      entries(10000).forEach(entry -> {
        this.snsTemplate.send(entry).addCallback(null, failureCallback);
      });
    });

    CompletableFuture.runAsync(() -> {
      entries(10000).forEach(entry -> {
        this.snsTemplate.send(entry).addCallback(null, failureCallback);
      });
    });

    this.snsTemplate.await().thenAccept(result -> {
      verify(failureCallback, timeout(300000).times(30000)).accept(any());
      verify(this.amazonSNS, atLeastOnce()).publishBatch(any(PublishBatchRequest.class));
    }).join();
  }

  @Test
  public void testFailRiseRuntimeException() {
    final String id = UUID.randomUUID().toString();

    when(this.amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenThrow(new RuntimeException());

    this.snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    this.snsTemplate.await().thenAccept(result -> {
      verify(this.amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
    }).join();

  }

  @Test
  public void testFailRiseAwsServiceException() {
    final String id = UUID.randomUUID().toString();

    when(this.amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenThrow(AwsServiceException.builder().awsErrorDetails(AwsErrorDetails.builder().build()).build());

    this.snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    this.snsTemplate.await().thenAccept(result -> {
      verify(this.amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
    }).join();

  }

  private List<RequestEntry<Object>> entries(final int amount) {
    final LinkedList<RequestEntry<Object>> entries = new LinkedList<>();

    for (int i = 0; i < amount; i++) {
      entries.add(RequestEntry.builder()
        .withSubject("subject")
        .withGroupId(UUID.randomUUID().toString())
        .withDeduplicationId(UUID.randomUUID().toString())
        .build());
    }

    return entries;
  }

}
// @formatter:on
