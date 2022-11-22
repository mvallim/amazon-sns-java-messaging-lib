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
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

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
@RunWith(MockitoJUnitRunner.class)
public class AmazonSnsProducerSyncTest {

  private AmazonSnsTemplate<Object> snsTemplate;

  @Mock
  private AmazonSNS amazonSNS;

  @Mock
  private TopicProperty topicProperty;

  @Before
  public void before() throws Exception {
    when(topicProperty.isFifo()).thenReturn(false);
    when(topicProperty.getTopicArn()).thenReturn("arn:aws:sns:us-east-2:000000000000:topic");
    when(topicProperty.getMaximumPoolSize()).thenReturn(2);
    when(topicProperty.getLinger()).thenReturn(50L);
    when(topicProperty.getMaxBatchSize()).thenReturn(10);
    snsTemplate = new AmazonSnsTemplate<>(amazonSNS, topicProperty);
  }

  @Test
  public void testSuccess() {
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

    snsTemplate.await().join();

    verify(amazonSNS, timeout(10000).times(1)).publishBatch(any());
  }

  @Test
  public void testFailure() {
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

    snsTemplate.await().join();

    verify(amazonSNS, timeout(10000).times(1)).publishBatch(any());
  }

  @Test
  public void testSuccessMultipleEntry() {

    when(amazonSNS.publishBatch(any())).thenAnswer(new Answer<PublishBatchResult>() {
      @Override
      public PublishBatchResult answer(final InvocationOnMock invocation) throws Throwable {
        final PublishBatchRequest request = invocation.getArgumentAt(0, PublishBatchRequest.class);
        final List<PublishBatchResultEntry> resultEntries = request.getPublishBatchRequestEntries().stream()
          .map(entry -> new PublishBatchResultEntry().withId(entry.getId()))
          .collect(Collectors.toList());
        return new PublishBatchResult().withSuccessful(resultEntries);
      }
    });

    final ConsumerHelper<ResponseSuccessEntry> successCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
    }));

    entries(100).forEach(entry -> {
      snsTemplate.send(entry).addCallback(successCallback);
    });

    entries(100).forEach(entry -> {
      snsTemplate.send(entry).addCallback(successCallback);
    });

    entries(100).forEach(entry -> {
      snsTemplate.send(entry).addCallback(successCallback);
    });

    snsTemplate.await().join();

    verify(successCallback, timeout(10000).times(300)).accept(any());
    verify(amazonSNS, atLeastOnce()).publishBatch(any());
  }

  @Test
  public void testFailureMultipleEntry() {

    when(amazonSNS.publishBatch(any())).thenAnswer(new Answer<PublishBatchResult>() {
      @Override
      public PublishBatchResult answer(final InvocationOnMock invocation) throws Throwable {
        final PublishBatchRequest request = invocation.getArgumentAt(0, PublishBatchRequest.class);
        final List<BatchResultErrorEntry> resultEntries = request.getPublishBatchRequestEntries().stream()
          .map(entry -> new BatchResultErrorEntry().withId(entry.getId()))
          .collect(Collectors.toList());
        return new PublishBatchResult().withFailed(resultEntries);
      }
    });

    final ConsumerHelper<ResponseFailEntry> failureCallback = spy(new ConsumerHelper<>(result -> {
      assertThat(result, notNullValue());
    }));

    entries(100).forEach(entry -> {
      snsTemplate.send(entry).addCallback(null, failureCallback);
    });

    entries(100).forEach(entry -> {
      snsTemplate.send(entry).addCallback(null, failureCallback);
    });

    entries(100).forEach(entry -> {
      snsTemplate.send(entry).addCallback(null, failureCallback);
    });

    snsTemplate.await().thenAccept(result -> snsTemplate.shutdown()).join();

    verify(failureCallback, timeout(10000).times(300)).accept(any());
    verify(amazonSNS, atLeastOnce()).publishBatch(any());
  }

  @Test
  public void testFailRiseRuntimeException() {
    final String id = UUID.randomUUID().toString();

    when(amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenThrow(new RuntimeException());

    snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().join();

    verify(amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
  }

  @Test
  public void testFailRiseAwsServiceException() {
    final String id = UUID.randomUUID().toString();

    when(amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenThrow(new AmazonServiceException("error"));

    snsTemplate.send(RequestEntry.builder().withId(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().join();

    verify(amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
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
