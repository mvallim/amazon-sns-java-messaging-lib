package com.amazon.sns.messaging.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TopicProperty {

  private final boolean fifo;

  private final String topicArn;

  private final long linger;

  private final int maxBatchSize;

}
