# amazon-sns-java-messaging-lib-template

Shared abstract module providing the core framework for batched Amazon SNS publishing. All SDK-specific implementations (`-v1`, `-v2`) depend on this module.

## Package Structure

```text
com.amazon.sns.messaging.lib
  ├── concurrent/          -- Thread pool, blocking queue, and thread factory utilities
  ├── core/                -- Abstract base classes and interfaces for producer/consumer patterns
  ├── exception/           -- Custom exceptions (e.g., MaximumAllowedMessageException)
  ├── metrics/             -- Micrometer-based metrics decorators (abstract)
  └── model/               -- Request/response data models and builders
```

## Key Components

### `core/`

| Class                                            | Description                                                                                                                                                                                        |
|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AbstractAmazonSnsTemplate<R,O,E>`               | High-level template. Composes a producer + consumer. Fluent builder with defaults: `ConcurrentHashMap` for pending requests, `RingBufferBlockingQueue`, identity decorator, `SimpleMeterRegistry`. |
| `AbstractAmazonSnsProducer<E>`                   | Enqueues `RequestEntry` into a `BlockingQueue`. Tracks lifecycle state (`RUNNIG`/`SHUTDOWN`).                                                                                                      |
| `AbstractAmazonSnsConsumer<C,R,O,E>`             | Periodic batch drain loop. Polls queue, assembles batches respecting 256 KB limit, publishes via SDK client, dispatches success/failure callbacks.                                                 |
| `AmazonSnsConsumer<R,O>`                         | Public interface: `publish()`, `handleError()`, `handleResponse()`, `shutdown()`, `await()`.                                                                                                       |
| `AmazonSnsProducer<E>`                           | Public interface: `send()`, `shutdown()`.                                                                                                                                                          |
| `ListenableFuture<S,F>` / `ListenableFutureImpl` | Simple future with success/failure callbacks. Tracks `NEW`/`SUCCESS`/`FAILURE` state.                                                                                                              |
| `AbstractMessageAttributes<V>`                   | Converts message header entries into typed attribute values (`String`, `Number`, `Binary`, `String.Array`, `Enum`).                                                                                |
| `RequestEntryInternalFactory`                    | Serializes payloads, computes attribute sizes, creates `RequestEntryInternal` objects for batch processing.                                                                                        |

### `concurrent/`

| Class                         | Description                                                                                                                                                                                    |
|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AmazonSnsThreadPoolExecutor` | `ThreadPoolExecutor` with zero core threads, `SynchronousQueue`, 30-second blocking rejection policy.                                                                                          |
| `RingBufferBlockingQueue<E>`  | Lock-based ring buffer (`AtomicReferenceArray`) with producer/consumer conditions. Only `put()` / `take()` supported; all else throws `UnsupportedOperationException`. Default capacity: 2048. |
| `BlockingSubmissionPolicy`    | Blocks caller up to a configurable timeout when thread pool is saturated.                                                                                                                      |
| `ThreadFactoryProvider`       | Selects virtual thread factories (Java 21+, reflection-based) or default daemon thread factories at runtime.                                                                                   |

### `metrics/`

| Class                                            | Description                                                                                                                                           |
|--------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AbstractAmazonSnsConsumerMetricsDecorator<I,O>` | Abstract Micrometer decorator. Tracks `sns.publish.attempts`, `sns.publish.success`, `sns.publish.failure`, duration, batch size, and inflight gauge. |
| `BlockingQueueMetricsDecorator<E>`               | Wraps a `BlockingQueue` with put/take counters, latency histograms, and queue size gauge.                                                             |
| `ExecutorServiceMetricsDecorator`                | Wraps an `ExecutorService` with active task count, success/failure counters, and task duration timer.                                                 |

### `model/`

| Class                        | Description                                                                                                                      |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `RequestEntry<T>`            | Single message: payload, headers, subject, FIFO group/deduplication IDs. Auto-generates `createTime` (nanoTime) and `id` (UUID). |
| `TopicProperty`              | Topic config: `fifo`, `maximumPoolSize`, `topicArn`, `linger`, `maxBatchSize`.                                                   |
| `ResponseSuccessEntry`       | Successful result: `id`, `messageId`, `sequenceNumber`.                                                                          |
| `ResponseFailEntry`          | Failure result: `id`, `message`, `code`, `senderFault`.                                                                          |
| `PublishRequestBuilder<R,E>` | Generic builder for SDK-specific batch request objects using a `BiFunction` supplier.                                            |

### `exception/`

| Class                            | Description                                             |
|----------------------------------|---------------------------------------------------------|
| `MaximumAllowedMessageException` | Thrown when a single serialized message exceeds 256 KB. |

## Dependencies

Managed by parent POM `com.github.mvallim:amazon-sns-java-messaging-lib:1.2.0-SNAPSHOT`:

```text
org.slf4j:slf4j-api:2.0.6
org.apache.commons:commons-lang3:3.20.0
org.apache.commons:commons-collections4:4.5.0
com.fasterxml.jackson.core:jackson-databind:2.16.1
io.micrometer:micrometer-core:1.16.3
org.projectlombok:lombok:1.18.42 (provided)
```

## Metrics

All metrics from this module are inherited by v1 and v2 implementations. See the [Technical Guide](../GUIDE.md#metrics-micrometer) for the complete reference.

## Usage

This module is not used directly. Import either `-v1` or `-v2`:

```xml
<dependency>
    <groupId>com.github.mvallim</groupId>
    <artifactId>amazon-sns-java-messaging-lib-v1</artifactId>
    <version>1.2.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>com.github.mvallim</groupId>
    <artifactId>amazon-sns-java-messaging-lib-v2</artifactId>
    <version>1.2.0</version>
</dependency>
```
