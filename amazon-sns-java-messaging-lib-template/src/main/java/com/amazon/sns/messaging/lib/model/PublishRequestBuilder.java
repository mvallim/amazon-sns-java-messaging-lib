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

import java.util.List;
import java.util.function.BiFunction;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PublishRequestBuilder {

  public static <R, E> Builder<R, E> builder() {
    return new Builder<>();
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  public static class Builder<R, E> {

    private BiFunction<String, List<E>, R> supplier;

    private String topicArn;

    private List<E> entries;

    public Builder<R, E> supplier(final BiFunction<String, List<E>, R> supplier) {
      this.supplier = supplier;
      return this;
    }

    public Builder<R, E> topicArn(final String topicArn) {
      this.topicArn = topicArn;
      return this;
    }

    public Builder<R, E> entries(final List<E> entries) {
      this.entries = entries;
      return this;
    }

    public R build() {
      return supplier.apply(topicArn, entries);
    }
  }

}
