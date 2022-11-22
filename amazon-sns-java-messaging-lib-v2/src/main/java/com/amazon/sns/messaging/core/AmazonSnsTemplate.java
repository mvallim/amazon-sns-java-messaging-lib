package com.amazon.sns.messaging.core;

import com.amazon.sns.messaging.model.TopicProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<PublishBatchRequest, PublishBatchResponse, E> {

  public AmazonSnsTemplate(final SnsClient amazonSNS, final TopicProperty topicProperty, final ObjectMapper objectMapper) {
    super.amazonSnsProducer = new AmazonSnsProducer<>(amazonSNS, topicProperty, objectMapper, super.pendingRequests, super.topicRequests);
    super.amazonSnsProducer.start();
  }

  public AmazonSnsTemplate(final SnsClient amazonSNS, final TopicProperty topicProperty) {
    this(amazonSNS, topicProperty, new ObjectMapper());
  }

}
