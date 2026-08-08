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

package com.amazon.sns.messaging.lib.exception;

/**
 * Exception thrown when a single serialized message exceeds the maximum allowed
 * size of 256 KB (262,144 bytes) imposed by Amazon SNS. Indicates a "poison"
 * request entry that must be dropped from the batch.
 *
 * @see PoisonRequestEntryException#fromMaximumAllowedMessage(String)
 */
public class MaximumAllowedMessageException extends PoisonRequestEntryException {

  private static final long serialVersionUID = -529663449633021689L;

  /**
   * Creates a new maximum allowed message exception.
   *
   * @param string the detail message
   */
  MaximumAllowedMessageException(final String string) {
    super(string);
  }

}
