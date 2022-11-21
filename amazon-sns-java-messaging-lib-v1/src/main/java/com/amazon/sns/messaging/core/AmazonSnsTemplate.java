package com.amazon.sns.messaging.core;

import com.amazon.sns.messaging.model.TopicProperty;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchResult;

public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<PublishBatchRequest, PublishBatchResult, E> {

  public AmazonSnsTemplate(final AmazonSNS amazonSNS, final TopicProperty topicProperty) {
    super.amazonSnsProducer = new AmazonSnsProducer<>(amazonSNS, topicProperty, super.pendingRequests, super.topicRequests);
    super.amazonSnsProducer.start();
  }

}
