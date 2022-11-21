package com.amazon.sns.messaging.core;

import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

public class AmazonSnsTemplate<E> extends AbstractAmazonSnsTemplate<PublishBatchRequest, PublishBatchResponse, E> {

  public AmazonSnsTemplate(final AmazonSnsProducer<E> amazonSnsProducer) {
    super(amazonSnsProducer);
  }

}
