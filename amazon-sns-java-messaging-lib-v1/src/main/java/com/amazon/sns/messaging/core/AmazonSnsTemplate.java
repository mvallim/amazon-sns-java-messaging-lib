package com.amazon.sns.messaging.core;

import com.amazon.sns.messaging.model.TopicProperty;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchResult;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<PublishBatchRequest, PublishBatchResult, E> {

  public AmazonSnsTemplate(final AmazonSNS amazonSNS, final TopicProperty topicProperty, final ObjectMapper objectMapper) {
    super.amazonSnsProducer = new AmazonSnsProducer<>(amazonSNS, topicProperty, objectMapper, super.pendingRequests, super.topicRequests);
    super.amazonSnsProducer.start();
  }

  public AmazonSnsTemplate(final AmazonSNS amazonSNS, final TopicProperty topicProperty) {
    this(amazonSNS, topicProperty, new ObjectMapper());
  }

}
