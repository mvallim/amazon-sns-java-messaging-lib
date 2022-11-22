package com.amazon.sns.messaging.core;

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

import com.amazon.sns.messaging.helper.ConsumerHelper;
import com.amazon.sns.messaging.model.RequestEntry;
import com.amazon.sns.messaging.model.ResponseFailEntry;
import com.amazon.sns.messaging.model.ResponseSuccessEntry;
import com.amazon.sns.messaging.model.TopicProperty;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.BatchResultErrorEntry;
import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchResult;
import com.amazonaws.services.sns.model.PublishBatchResultEntry;

// @formatter:off
@RunWith(MockitoJUnitRunner.class)
public class AmazonSnsProducerAsyncTest {

  private AmazonSnsTemplate<Object> snsTemplate;

  @Mock
  private AmazonSNS amazonSNS;

  @Mock
  private TopicProperty topicProperty;

  @Before
  public void before() throws Exception {
    when(topicProperty.isFifo()).thenReturn(true);
    when(topicProperty.getTopicArn()).thenReturn("arn:aws:sns:us-east-2:000000000000:topic");
    when(topicProperty.getMaximumPoolSize()).thenReturn(100);
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

    snsTemplate.send(RequestEntry.builder().id(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().thenAccept(result -> snsTemplate.shutdown()).join();

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

    snsTemplate.send(RequestEntry.builder().id(id).build()).addCallback(null, result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().thenAccept(result -> snsTemplate.shutdown()).join();

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

    snsTemplate.await().thenAccept(result -> snsTemplate.shutdown()).join();

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

    snsTemplate.send(RequestEntry.builder().id(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().thenAccept(result -> snsTemplate.shutdown()).join();

    verify(amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
  }

  @Test
  public void testFailRiseAwsServiceException() {
    final String id = UUID.randomUUID().toString();

    when(amazonSNS.publishBatch(any(PublishBatchRequest.class))).thenThrow(new AmazonServiceException("error"));

    snsTemplate.send(RequestEntry.builder().id(id).build()).addCallback(result -> {
      assertThat(result, notNullValue());
      assertThat(result.getId(), is(id));
    });

    snsTemplate.await().thenAccept(result -> snsTemplate.shutdown()).join();

    verify(amazonSNS, timeout(10000).times(1)).publishBatch(any(PublishBatchRequest.class));
  }

  private List<RequestEntry<Object>> entries(final int amount) {
    final LinkedList<RequestEntry<Object>> entries = new LinkedList<>();

    for (int i = 0; i < amount; i++) {
      entries.add(RequestEntry.builder()
        .subject("subject")
        .groupId(UUID.randomUUID().toString())
        .deduplicationId(UUID.randomUUID().toString())
        .build());
    }

    return entries;
  }

}
// @formatter:on
