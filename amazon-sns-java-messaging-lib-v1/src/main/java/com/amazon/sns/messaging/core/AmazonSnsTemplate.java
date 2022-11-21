package com.amazon.sns.messaging.core;

import com.amazonaws.services.sns.model.PublishBatchRequest;
import com.amazonaws.services.sns.model.PublishBatchResult;

public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<PublishBatchRequest, PublishBatchResult, E> {

  public AmazonSnsTemplate(final AmazonSnsProducer<E> amazonSnsProducer) {
    super(amazonSnsProducer);
  }

}
