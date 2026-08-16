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

package com.amazon.sns.messaging.lib.helpers;

import java.util.LinkedList;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BenchmarkPayloads {

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class SmallMessage {

    private String id;

    private String body;

    private long timestamp;

    private boolean successful;

    private Attributes attributes;

    public static SmallMessage sample() {
      return new SmallMessage("msg-0000000001", "The quick brown fox jumps over the lazy dog.", 1_700_000_000_000L, true, new Attributes("us-east-1", "standard", 3));
    }

    public static SmallMessage sample(final String id) {
      return new SmallMessage(id, "The quick brown fox jumps over the lazy dog.", 1_700_000_000_000L, true, new Attributes("us-east-1", "standard", 3));
    }
  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Attributes {

    private String region;

    private String tier;

    private int retryCount;

  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class LargeMessage {

    private String batchId;

    private final List<SmallMessage> entries = new LinkedList<>();

    private final List<Tag> tags = new LinkedList<>();

    public static LargeMessage sample(final int entryCount) {
      final LargeMessage m = new LargeMessage("batch-0000000001");

      for (int i = 0; i < entryCount; i++) {
        m.entries.add(SmallMessage.sample("msg-" + i));
      }

      for (int i = 0; i < 20; i++) {
        m.tags.add(new Tag("key-" + i, "value-" + i));
      }

      return m;
    }

  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Tag {

    private String key;

    private String value;

  }

}