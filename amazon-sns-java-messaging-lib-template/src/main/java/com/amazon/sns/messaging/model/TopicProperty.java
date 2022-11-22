package com.amazon.sns.messaging.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class TopicProperty {

  private final boolean fifo;

  private final Integer maximumPoolSize;

  private final String topicArn;

  private final long linger;

  private final int maxBatchSize;

}
