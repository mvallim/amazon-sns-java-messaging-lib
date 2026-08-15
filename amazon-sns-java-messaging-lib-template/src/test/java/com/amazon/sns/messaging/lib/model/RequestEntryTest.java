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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

// @formatter:off
class RequestEntryTest {

  @Test
  void testSuccess() {
    final Map<String, Object> messageHeaders = new HashMap<>();

    final RequestEntry<Object> requestEntry = RequestEntry.builder()
      .withCreateTime(12345L)
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

  @Test
  void testSuccessMissingFields() {
    final RequestEntry<Object> requestEntry = RequestEntry.builder()
      .withValue("value")
      .build();

    assertThat(requestEntry.getCreateTime(), greaterThan(0L));
    assertThat(requestEntry.getDeduplicationId(), nullValue());
    assertThat(requestEntry.getGroupId(), nullValue());
    assertThat(requestEntry.getId(), not(nullValue()));
    assertThat(requestEntry.getMessageHeaders().entrySet(), empty());
    assertThat(requestEntry.getSubject(), nullValue());
    assertThat(requestEntry.getValue(), is("value"));
  }

}
// @formatter:on
