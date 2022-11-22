package com.amazon.sns.messaging.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

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
