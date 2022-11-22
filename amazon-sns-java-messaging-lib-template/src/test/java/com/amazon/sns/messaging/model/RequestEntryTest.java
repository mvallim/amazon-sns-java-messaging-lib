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

package com.amazon.sns.messaging.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.amazon.sns.messaging.lib.model.RequestEntry;

// @formatter:off
public class RequestEntryTest {

  @Test
  public void testSuccess() {
    final Map<String, Object> messageHeaders = new HashMap<>();

    final RequestEntry<Object> requestEntry = RequestEntry.builder()
      .withCreateTime(12345)
      .withDeduplicationId("deduplicationId")
      .withGroupId("groupId")
      .withId("id")
      .withMessageHeaders(messageHeaders)
      .withSubject("subject")
      .withValue("value")
      .build();

    assertThat(requestEntry.getCreateTime(), equalTo(12345L));
    assertThat(requestEntry.getDeduplicationId(), equalTo("deduplicationId"));
    assertThat(requestEntry.getGroupId(), equalTo("groupId"));
    assertThat(requestEntry.getId(), equalTo("id"));
    assertThat(requestEntry.getMessageHeaders(), equalTo(messageHeaders));
    assertThat(requestEntry.getSubject(), equalTo("subject"));
    assertThat(requestEntry.getValue(), equalTo("value"));
  }

}
// @formatter:on
