package com.amazon.sns.messaging.core;

import java.util.Map;

import software.amazon.awssdk.services.sns.SnsClient;

public class AmazonSnsClientAdapter extends AbstractAmazonSnsClientAdapter<SnsClient> {

  public AmazonSnsClientAdapter(final SnsClient client) {
    super(client);
  }

  @Override
  public ListenableFuture<SuccessResult, FailureResult> publish(final Object request, final Map<String, Object> messageHeaders) {
    return null;
  }

}
