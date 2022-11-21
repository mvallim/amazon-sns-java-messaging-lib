package com.amazon.sns.messaging.model;

import java.io.Serializable;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class ResponseSuccessEntry implements Serializable {

  private static final long serialVersionUID = 4864967607600926557L;

  private final String id;

  private final String messageId;

  private final String sequenceNumber;

}
