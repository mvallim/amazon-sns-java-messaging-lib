package com.amazon.sns.messaging.core;

import com.amazon.sns.messaging.model.TopicProperty;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<PublishBatchRequest, PublishBatchResponse, E> {

  public AmazonSnsTemplate(final SnsClient amazonSNS, final TopicProperty topicProperty) {
    super.amazonSnsProducer = new AmazonSnsProducer<>(amazonSNS, topicProperty, super.pendingRequests, super.topicRequests);
  }

}
