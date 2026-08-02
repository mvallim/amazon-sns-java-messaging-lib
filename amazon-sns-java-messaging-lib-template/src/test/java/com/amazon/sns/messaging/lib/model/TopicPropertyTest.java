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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.model.TopicProperty.TopicPropertyBuilder;

// @formatter:off
class TopicPropertyTest {

  private static final String VALID_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:my-topic";
  private static final String VALID_FIFO_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:my-topic.fifo";

  private TopicPropertyBuilder validBuilder() {
    return TopicProperty.builder().fifo(false).maximumPoolSize(5).topicArn(VALID_TOPIC_ARN).linger(10L).maxBatchSize(10);
  }

  @Test
  void testBuildsSuccessfullyWithValidProperties() {
    final TopicProperty topicProperty = validBuilder().build();

    assertThat(topicProperty.isFifo(), is(false));
    assertThat(topicProperty.getMaximumPoolSize(), is(equalTo(5)));
    assertThat(topicProperty.getTopicArn(), is(equalTo(VALID_TOPIC_ARN)));
    assertThat(topicProperty.getLinger(), is(equalTo(10L)));
    assertThat(topicProperty.getMaxBatchSize(), is(equalTo(10)));
  }

  @Test
  void testToBuilderReturnsEquivalentInstance() {
    final TopicProperty original = validBuilder().build();

    final TopicProperty copy = original.toBuilder().build();

    assertThat(copy.isFifo(), is(equalTo(original.isFifo())));
    assertThat(copy.getMaximumPoolSize(), is(equalTo(original.getMaximumPoolSize())));
    assertThat(copy.getTopicArn(), is(equalTo(original.getTopicArn())));
    assertThat(copy.getLinger(), is(equalTo(original.getLinger())));
    assertThat(copy.getMaxBatchSize(), is(equalTo(original.getMaxBatchSize())));
  }

  @Test
  void testThrowsWhenMaximumPoolSizeIsNull() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().maximumPoolSize(null);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'maximumPoolSize' is required"));
  }

  @Test
  void testThrowsWhenMaximumPoolSizeIsZero() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().maximumPoolSize(0);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'maximumPoolSize' must be greater than zero"));
  }

  @Test
  void testThrowsWhenMaximumPoolSizeIsNegative() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().maximumPoolSize(-1);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'maximumPoolSize' must be greater than zero"));
  }

  @Test
  void testThrowsWhenFifoTrueAndMaximumPoolSizeIsNotOne() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().fifo(true).maximumPoolSize(2).topicArn(VALID_FIFO_TOPIC_ARN);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'maximumPoolSize' must be equal to 1 (one) when 'fifo' is true"));
  }

  @Test
  void testBuildsSuccessfullyWhenFifoTrueAndMaximumPoolSizeIsOne() {
    final TopicProperty topicProperty = validBuilder().fifo(true).maximumPoolSize(1).topicArn(VALID_FIFO_TOPIC_ARN).build();

    assertThat(topicProperty.isFifo(), is(true));
    assertThat(topicProperty.getMaximumPoolSize(), is(equalTo(1)));
    assertThat(topicProperty.getTopicArn(), is(equalTo(VALID_FIFO_TOPIC_ARN)));
  }

  @Test
  void testThrowsWhenTopicArnIsNull() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().topicArn(null);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'topicArn' is required"));
  }

  @Test
  void testThrowsWhenTopicArnIsEmpty() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().topicArn("");

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'topicArn' is required"));
  }

  @Test
  void testThrowsWhenTopicArnIsBlank() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().topicArn("   ");

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'topicArn' is required"));
  }

  @Test
  void testThrowsWhenTopicArnHasInvalidFormat() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().topicArn("not-a-valid-arn");

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'topicArn' must have the correct arn format 'arn:aws:sns:<region>:<account-id>:<topic-name>'"));
  }

  @Test
  void testThrowsWhenTopicArnAccountIdIsNotTwelveDigits() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().topicArn("arn:aws:sns:us-east-1:123:my-topic");

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'topicArn' must have the correct arn format 'arn:aws:sns:<region>:<account-id>:<topic-name>'"));
  }

  @Test
  void testBuildsSuccessfullyWithFifoTopicArnSuffix() {
    final TopicProperty topicProperty = validBuilder().fifo(true).maximumPoolSize(1).topicArn(VALID_FIFO_TOPIC_ARN).build();

    assertThat(topicProperty.getTopicArn(), is(equalTo(VALID_FIFO_TOPIC_ARN)));
  }

  @Test
  void testThrowsWhenLingerIsLessThanTen() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().linger(9L);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'linger' must be greater than or equal to 10 (ten)"));
  }

  @Test
  void testThrowsWhenLingerIsNegative() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().linger(-1L);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'linger' must be greater than or equal to 10 (ten)"));
  }

  @Test
  void testBuildsSuccessfullyWhenLingerIsExactlyTen() {
    final TopicProperty topicProperty = validBuilder().linger(10L).build();

    assertThat(topicProperty.getLinger(), is(equalTo(10L)));
  }

  @Test
  void testThrowsWhenMaxBatchSizeIsLessThanOne() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().maxBatchSize(0);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'maxBatchSize' must be in the range of 1 (one) to 10 (ten)"));
  }

  @Test
  void testThrowsWhenMaxBatchSizeIsGreaterThanTen() {
    final TopicProperty.TopicPropertyBuilder builder = validBuilder().maxBatchSize(11);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), containsString("'maxBatchSize' must be in the range of 1 (one) to 10 (ten)"));
  }

  @Test
  void testBuildsSuccessfullyWhenMaxBatchSizeIsAtLowerBoundary() {
    final TopicProperty topicProperty = validBuilder().maxBatchSize(1).build();

    assertThat(topicProperty.getMaxBatchSize(), is(equalTo(1)));
  }

  @Test
  void testBuildsSuccessfullyWhenMaxBatchSizeIsAtUpperBoundary() {
    final TopicProperty topicProperty = validBuilder().maxBatchSize(10).build();

    assertThat(topicProperty.getMaxBatchSize(), is(equalTo(10)));
  }

  @Test
  void testExceptionIsThrownWithNonEmptyValidationMessageWhenMultipleFieldsAreInvalid() {
    final TopicProperty.TopicPropertyBuilder builder = TopicProperty.builder().fifo(false).maximumPoolSize(null).topicArn(null).linger(0L).maxBatchSize(0);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

    assertThat(exception.getMessage(), is(notNullValue()));
  }

  @Test
  void testToStringDoesNotThrow() {
    final TopicProperty topicProperty = validBuilder().build();

    assertThat(topicProperty.toString(), is(notNullValue()));
  }

}
// @formatter:on