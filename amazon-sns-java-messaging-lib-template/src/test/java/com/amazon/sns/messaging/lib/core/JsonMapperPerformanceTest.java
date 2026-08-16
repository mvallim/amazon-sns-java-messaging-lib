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

package com.amazon.sns.messaging.lib.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.fory.json.ForyJson;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.amazon.sns.messaging.lib.helpers.BenchmarkPayloads.LargeMessage;
import com.amazon.sns.messaging.lib.helpers.BenchmarkPayloads.SmallMessage;
import com.amazon.sns.messaging.lib.helpers.TrySupplier;
import com.fasterxml.jackson.databind.ObjectMapper;

// @formatter:off
@Tag("performance")
class JsonMapperPerformanceTest {

  private static final int WARMUP_ITERATIONS = 20_000;

  private static final int MEASURED_ITERATIONS = 100_000;

  private static final int LARGE_ENTRY_COUNT = 50;

  private static final double MAX_RELATIVE_SLOWDOWN = 5.0;

  private static Stream<Arguments> providePayloads() {
    return Stream.of(
      Arguments.of("small", (Supplier<Object>) SmallMessage::sample, SmallMessage.class),
      Arguments.of("large", (Supplier<Object>) () -> LargeMessage.sample(LARGE_ENTRY_COUNT), LargeMessage.class)
    );
  }

  @Timeout(120)
  @MethodSource("providePayloads")
  @ParameterizedTest(name = ParameterizedTest.ARGUMENTS_PLACEHOLDER)
  <T> void testToJsonAndFromJsonThroughput(
      final String payloadLabel, final Supplier<Object> payloadSupplier,
      final Class<T> payloadType) throws Exception {
    System.out.println(String.format("%n=== payload=%s ===", payloadLabel));

    final JsonMapper jackson = JsonMapperFactory.create(new ObjectMapper());
    final JsonMapper fory = JsonMapperFactory.create(ForyJson.builder().build());
    final Object payload = payloadSupplier.get();

    final String jacksonJson = jackson.toJson(payload);
    final String foryJson = fory.toJson(payload);
    final byte[] jacksonBytes = jackson.toJsonBytes(payload);
    final byte[] foryBytes = fory.toJsonBytes(payload);

    assertOperation("toJson", payloadLabel,
      benchmark(() -> jackson.toJson(payload)),
      benchmark(() -> fory.toJson(payload)));

    System.out.println("---");

    assertOperation("toJsonBytes", payloadLabel,
      benchmark(() -> jackson.toJsonBytes(payload)),
      benchmark(() -> fory.toJsonBytes(payload)));

    System.out.println("---");

    assertOperation("fromJson(String)", payloadLabel,
      benchmark(() -> jackson.fromJson(jacksonJson, payloadType)),
      benchmark(() -> fory.fromJson(foryJson, payloadType)));

    System.out.println("---");

    assertOperation("fromJson(bytes)", payloadLabel,
      benchmark(() -> jackson.fromJson(jacksonBytes, payloadType)),
      benchmark(() -> fory.fromJson(foryBytes, payloadType)));
  }

  private void assertOperation(
      final String operation,
      final String payloadLabel,
      final double jacksonOpsPerSec,
      final double foryOpsPerSec) {
    report(operation + " (jackson)", jacksonOpsPerSec);
    report(operation + " (fory)", foryOpsPerSec);

    final double faster = Math.max(jacksonOpsPerSec, foryOpsPerSec);
    final double slower = Math.min(jacksonOpsPerSec, foryOpsPerSec);
    final double relativeSlowdown = faster / slower;

    assertThat(relativeSlowdown)
        .as("%s/%s: neither backend should be more than %.1fx slower than the other (jackson=%.0f fory=%.0f ops/sec)",
            payloadLabel, operation, MAX_RELATIVE_SLOWDOWN, jacksonOpsPerSec, foryOpsPerSec)
        .isLessThan(MAX_RELATIVE_SLOWDOWN);
  }

  private static void report(final String label, final double opsPerSec) {
    System.out.printf(String.format("%-30s %,15.0f ops/sec%n", label, opsPerSec));
  }

  private double benchmark(final TrySupplier<?> op) throws Exception {
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      op.get();
    }

    final long start = System.nanoTime();

    for (int i = 0; i < MEASURED_ITERATIONS; i++) {
      op.get();
    }

    final long elapsedNanos = System.nanoTime() - start;

    return MEASURED_ITERATIONS / (elapsedNanos / 1_000_000_000.0);
  }

}
// @formatter:on