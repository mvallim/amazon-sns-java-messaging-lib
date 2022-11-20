package com.amazon.sns.messaging.core;

import java.util.Map;

import com.amazonaws.services.sns.AmazonSNS;

public class AmazonSnsClientAdapter extends AbstractAmazonSnsClientAdapter<AmazonSNS> {

  public AmazonSnsClientAdapter(final AmazonSNS client) {
    super(client);
  }

  @Override
  public ListenableFuture<SuccessResult, FailureResult> publish(final Object request, final Map<String, Object> messageHeaders) {
    return null;
  }

}
