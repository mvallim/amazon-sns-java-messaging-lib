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

import static br.com.fluentvalidator.predicate.ComparablePredicate.betweenInclusive;
import static br.com.fluentvalidator.predicate.ComparablePredicate.equalTo;
import static br.com.fluentvalidator.predicate.ComparablePredicate.greaterThan;
import static br.com.fluentvalidator.predicate.ComparablePredicate.greaterThanOrEqual;
import static br.com.fluentvalidator.predicate.LogicalPredicate.not;
import static br.com.fluentvalidator.predicate.ObjectPredicate.nullValue;
import static br.com.fluentvalidator.predicate.StringPredicate.stringMatches;
import static java.util.function.Function.identity;

import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import br.com.fluentvalidator.AbstractValidator;
import br.com.fluentvalidator.context.ValidationResult;
import br.com.fluentvalidator.predicate.PredicateBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// @formatter:off
/**
 * Configuration properties for an Amazon SNS topic.
 */
@Getter
@ToString
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TopicProperty {

  private static final long DEFAULT_LINGER = 10L;

  /**
   * Whether the topic is a FIFO topic.
   */
  private final boolean fifo;

  /**
   * The maximum number of threads in the pool for concurrent publishing.
   */
  private final Integer maximumPoolSize;

  /**
   * The ARN of the SNS topic.
   */
  private final String topicArn;

  /**
   * The batching linger time in milliseconds.
   */
  private final long linger;

  /**
   * The maximum number of messages per batch.
   */
  private final int maxBatchSize;

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  static final class TopicPropertyValidator extends AbstractValidator<TopicProperty> {

    public static final TopicPropertyValidator INSTANCE = new TopicPropertyValidator();

    @Override
    public void rules() {

      failFastRule();

      ruleFor("maximumPoolSize", TopicProperty::getMaximumPoolSize)
        .must(not(nullValue()))
          .withMessage("'maximumPoolSize' is required")
        .must(greaterThan(0))
          .when(not(nullValue()))
          .withMessage("'maximumPoolSize' must be greater than zero");

      ruleFor("topicArn", TopicProperty::getTopicArn)
        .must(StringUtils::isNotBlank)
          .withMessage("'topicArn' is required")
        .must(stringMatches("^arn:aws:sns:[^:]+:\\d{12}:([\\w-]{1,256}|[\\w-]{1,251}\\.fifo)$"))
          .when(StringUtils::isNotBlank)
          .withMessage("'topicArn' must have the correct arn format 'arn:aws:sns:<region>:<account-id>:<topic-name>'");

      ruleFor("linger", TopicProperty::getLinger)
        .must(greaterThanOrEqual(DEFAULT_LINGER))
          .withMessage("'linger' must be greater than or equal to 10 (ten)");

      ruleFor("maxBatchSize", TopicProperty::getMaxBatchSize)
        .must(betweenInclusive(1, 10))
          .withMessage("'maxBatchSize' must be in the range of 1 (one) to 10 (ten)");

      ruleFor(identity())
        .must(equalTo(TopicProperty::getMaximumPoolSize, 1))
          .when(TopicProperty::isFifo)
          .withFieldName("maximumPoolSize")
          .withMessage("'maximumPoolSize' must be equal to 1 (one) when 'fifo' is true")
          .withAttempedValue(TopicProperty::getMaximumPoolSize)
        .must(stringEndsWith(TopicProperty::getTopicArn, ".fifo"))
          .when(TopicProperty::isFifo)
          .withFieldName("topicArn")
          .withMessage("'topicArn' must be ends with in '.fifo' when 'fifo' is true")
          .withAttempedValue(TopicProperty::getTopicArn);
    }

    private static <T> Predicate<T> stringEndsWith(final Function<T, String> source, final String ends) {
      return PredicateBuilder.<T>from(not(nullValue())).and(obj -> source.apply(obj).endsWith(ends));
    }

  }

  @SuppressWarnings("java:S116")
  public static class TopicPropertyBuilder {

    /**
     * Tracks whether {@code linger(long)} was explicitly invoked.
     *
     * <p>This flag allows applying {@link TopicProperty#DEFAULT_LINGER} only when
     * no explicit value was provided through the builder.
     */
    private boolean linger$set;

    public TopicPropertyBuilder linger(final long linger) {
      this.linger = linger;
      linger$set = true;
      return this;
    }

    public TopicProperty build() {
      final long linger = linger$set ? this.linger : DEFAULT_LINGER;

      final TopicProperty topicProperty = new TopicProperty(fifo, maximumPoolSize, topicArn, linger, maxBatchSize);

      final ValidationResult validationResult = TopicPropertyValidator.INSTANCE.validate(topicProperty);

      if (CollectionUtils.isNotEmpty(validationResult.getErrors())) {
        throw new IllegalArgumentException(validationResult.toString());
      }

      return topicProperty;
    }

  }

}
//@formatter:on