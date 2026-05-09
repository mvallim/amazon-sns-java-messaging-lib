/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.sns.messaging.lib.model;

import java.io.Serializable;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a successful publish result from Amazon SNS.
 */
@Getter
@ToString
@Builder(setterPrefix = "with")
public class ResponseSuccessEntry implements Serializable {

  private static final long serialVersionUID = 4864967607600926557L;

  /**
   * The unique identifier of the original request.
   */
  private final String id;

  /**
   * The message ID assigned by Amazon SNS.
   */
  private final String messageId;

  /**
   * The sequence number (for FIFO topics).
   */
  private final String sequenceNumber;

}
