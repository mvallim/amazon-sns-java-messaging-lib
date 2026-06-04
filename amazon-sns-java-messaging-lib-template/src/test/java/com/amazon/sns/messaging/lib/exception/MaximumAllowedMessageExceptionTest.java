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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.model.RequestEntry;

class MaximumAllowedMessageExceptionTest {

  private static final String ERROR_MESSAGE = "Message exceeds the maximum allowed size of 256 KB";

  private RequestEntry<String> buildRequestEntry() {
    return RequestEntry.<String>builder().withId("req-1").withValue("oversized payload").build();
  }

  @Nested
  class HierarchyAndContract {

    @Test
    void testIsInstanceOfRuntimeException() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, buildRequestEntry());

      assertThat(ex, is(instanceOf(RuntimeException.class)));
    }

    @Test
    void testIsInstanceOfException() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, buildRequestEntry());

      assertThat(ex, is(instanceOf(Exception.class)));
    }

  }

  @Nested
  class Constructor {

    @Test
    void testConstructorSetsDetailMessage() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, buildRequestEntry());

      assertThat(ex.getMessage(), equalTo(ERROR_MESSAGE));
    }

    @Test
    void testConstructorSetsRequestEntry() {
      final RequestEntry<String> entry = buildRequestEntry();
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, entry);

      assertThat(ex.getRequest(), is(sameInstance(entry)));
    }

    @Test
    void testConstructorWithNullMessageSetsNullMessage() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(null, buildRequestEntry());

      assertThat(ex.getMessage(), is(nullValue()));
    }

    @Test
    void testConstructorWithNullRequestSetsNullRequest() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, null);

      assertThat(ex.getRequest(), is(nullValue()));
    }

    @Test
    void testConstructorWithBothNullsDoesNotThrow() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(null, null);

      assertThat(ex, is(notNullValue()));
    }

    @Test
    void testConstructorWithEmptyMessageSetsEmptyMessage() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException("", buildRequestEntry());

      assertThat(ex.getMessage(), equalTo(""));
    }
  }

  @Nested
  class GetMessage {

    @Test
    void testGetMessageReturnsExactString() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, buildRequestEntry());

      assertThat(ex.getMessage(), equalTo(ERROR_MESSAGE));
    }

    @Test
    void testGetMessageWithSpecialCharactersPreservesContent() {
      final String special = "Error: size=262144 > max=262144 \u00e9\u00e0";
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(special, buildRequestEntry());

      assertThat(ex.getMessage(), equalTo(special));
    }

    @Test
    void testGetMessageIsNotNull() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, buildRequestEntry());

      assertThat(ex.getMessage(), is(notNullValue()));
    }
  }

  @Nested
  class GetRequest {

    @Test
    void testGetRequestReturnsNotNull() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, buildRequestEntry());

      assertThat(ex.getRequest(), is(notNullValue()));
    }

    @Test
    void testGetRequestReturnsSameInstancePassedToConstructor() {
      final RequestEntry<String> entry = buildRequestEntry();
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, entry);

      assertThat(ex.<String>getRequest(), is(sameInstance(entry)));
    }

    @Test
    void testGetRequestWithTypedPayloadReturnsCorrectId() {
      final RequestEntry<String> entry = buildRequestEntry();
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, entry);

      assertThat(ex.<String>getRequest().getId(), equalTo("req-1"));
    }

    @Test
    void testGetRequestWithTypedPayloadReturnsCorrectValue() {
      final RequestEntry<String> entry = buildRequestEntry();
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, entry);

      assertThat(ex.<String>getRequest().getValue(), equalTo("oversized payload"));
    }

    @Test
    void testGetRequestWithIntegerPayload() {
      final RequestEntry<Integer> entry = RequestEntry.<Integer>builder().withId("req-int").withValue(999).build();

      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, entry);

      assertThat(ex.<Integer>getRequest().getValue(), equalTo(999));
    }

    @Test
    void testGetRequestReturnsNullWhenNullPassedToConstructor() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, null);

      assertThat(ex.getRequest(), is(nullValue()));
    }
  }

  @Nested
  class Throwability {

    @Test
    void testExceptionCanBeThrown() {
      final RequestEntry<String> entry = buildRequestEntry();

      try {
        throw new MaximumAllowedMessageException(ERROR_MESSAGE, entry);
      } catch (final MaximumAllowedMessageException ex) {
        assertThat(ex.getMessage(), equalTo(ERROR_MESSAGE));
        assertThat(ex.getRequest(), is(sameInstance(entry)));
      }
    }

    @Test
    void testExceptionCanBeCaughtAsRuntimeException() {
      final RequestEntry<String> entry = buildRequestEntry();

      try {
        throw new MaximumAllowedMessageException(ERROR_MESSAGE, entry);
      } catch (final RuntimeException ex) {
        assertThat(ex, is(instanceOf(MaximumAllowedMessageException.class)));
      }
    }

    @Test
    void testExceptionPreservesStackTrace() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, buildRequestEntry());

      assertThat(ex.getStackTrace(), is(notNullValue()));
      assertThat(ex.getStackTrace().length, is(greaterThan(0)));
    }

    @Test
    void testExceptionCauseIsNullByDefault() {
      final MaximumAllowedMessageException ex = new MaximumAllowedMessageException(ERROR_MESSAGE, buildRequestEntry());

      assertThat(ex.getCause(), is(nullValue()));
    }
  }

  private static org.hamcrest.Matcher<Integer> greaterThan(final int value) {
    return org.hamcrest.Matchers.greaterThan(value);
  }

}