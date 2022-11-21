package com.amazon.sns.messaging.model;

import java.io.Serializable;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class ResponseFailEntry implements Serializable {

  private static final long serialVersionUID = 6271096607211902145L;

  private final String id;

  private final String message;

  private final String code;

  private final Boolean senderFault;

}
