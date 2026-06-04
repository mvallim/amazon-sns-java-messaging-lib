# amazon-sns-java-messaging-lib-v2

AWS SDK v2 implementation of the Amazon SNS Java Messaging Library. Provides batched message publishing to SNS using `software.amazon.awssdk:sns:2.20.162`.

## Package Structure

```
com.amazon.sns.messaging.lib
  ├── core/
  │   ├── AmazonSnsTemplate.java         -- Public API entry point
  │   ├── AmazonSnsProducerImpl.java     -- Producer (enqueues requests)
  │   ├── AmazonSnsConsumerImpl.java     -- Consumer (calls SnsClient.publishBatch)
  │   └── MessageAttributes.java         -- Header-to-MessageAttributeValue converter
  └── metrics/
      └── AmazonSnsConsumerMetricsDecorator.java  -- Micrometer metrics decorator
```

## Key Classes

| Class | Description |
|-------|-------------|
| `AmazonSnsTemplate<E>` | Extends `AbstractAmazonSnsTemplate`. Primary API: `send()`, `shutdown()`, `await()`. Use the builder: `AmazonSnsTemplate.builder(snsClient, topicProperty)`. Deprecated constructors available for backward compatibility. |
| `AmazonSnsProducerImpl<E>` | Extends `AbstractAmazonSnsProducer`. Thin wrapper that enqueues `RequestEntry` into a shared blocking queue. |
| `AmazonSnsConsumerImpl<E>` | Extends `AbstractAmazonSnsConsumer`. Calls `SnsClient.publishBatch()` with v2 `PublishBatchRequest`/`PublishBatchResponse`. Handles per-entry success/failure from batch response. |
| `MessageAttributes` | Extends `AbstractMessageAttributes<MessageAttributeValue>`. Converts header entries to v2 `MessageAttributeValue` objects (String, Number, Binary via `SdkBytes`, String.Array, Enum). |
| `AmazonSnsConsumerMetricsDecorator` | Extends `AbstractAmazonSnsConsumerMetricsDecorator<PublishBatchRequest, PublishBatchResponse>`. Records publish attempts, latency, batch size, inflight count. Tags failures by `AwsServiceException` error code. |

## Dependencies

```
com.github.mvallim:amazon-sns-java-messaging-lib-template:1.2.0-SNAPSHOT
software.amazon.awssdk:sns:2.20.162
```

Test-only:
```
software.amazon.awssdk:sqs:2.20.162 (test scope)
```

## Usage

### Standard SNS Topic

```java
SnsClient snsClient = SnsClient.create();

TopicProperty topicProperty = TopicProperty.builder()
    .fifo(false)
    .linger(100)
    .maxBatchSize(10)
    .maximumPoolSize(20)
    .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
    .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(snsClient, topicProperty).build();

RequestEntry<MyMessage> entry = RequestEntry.<MyMessage>builder()
    .withValue(new MyMessage())
    .withMessageHeaders(Map.of("header1", "value1"))
    .build();

snsTemplate.send(entry);
snsTemplate.shutdown();
```

### FIFO SNS Topic

```java
TopicProperty topicProperty = TopicProperty.builder()
    .fifo(true)
    .linger(100)
    .maxBatchSize(10)
    .maximumPoolSize(20)
    .topicArn("arn:aws:sns:us-east-2:000000000000:topic.fifo")
    .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(snsClient, topicProperty).build();

RequestEntry<MyMessage> entry = RequestEntry.<MyMessage>builder()
    .withValue(new MyMessage())
    .withGroupId(UUID.randomUUID().toString())
    .withDeduplicationId(UUID.randomUUID().toString())
    .build();

snsTemplate.send(entry).addCallback(
    success -> LOGGER.info("Sent: {}", success),
    failure -> LOGGER.error("Failed: {}", failure)
);
snsTemplate.await().join();
```

### With Custom ObjectMapper and Queue

```java
AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(snsClient, topicProperty)
    .objectMapper(new ObjectMapper())
    .topicRequests(new LinkedBlockingQueue<>(100))
    .publishDecorator(req -> req)
    .build();
```

### Using Micrometer Metrics

```java
MeterRegistry meterRegistry = new CompositeMeterRegistry();
meterRegistry.add(new JmxMeterRegistry());

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(snsClient, topicProperty)
    .meterRegistry(meterRegistry)
    .build();
```

## Metrics

When a `MeterRegistry` is provided via `.meterRegistry()`, the following metrics are recorded:

| Metric | Type | Tags | Description |
|---|---|---|---|
| `sns.publish.attempts` | Counter | `topic` | Total PublishBatch attempts |
| `sns.publish.success` | Counter | `topic` | Successful messages |
| `sns.publish.failure` | Counter | `topic`, `error_code`, `error_type` | Failed messages |
| `sns.publish.duration` | Timer (p50/p95/p99) | `topic` | Publish latency |
| `sns.publish.batch.size` | DistributionSummary | `topic` | Messages per batch |
| `sns.publish.inflight` | Gauge | `topic` | In-flight publish batches |
| `blocking.queue.puts.total` | Counter | `name` | Successful put operations |
| `blocking.queue.puts.failed` | Counter | `name` | Failed put operations |
| `blocking.queue.put.duration` | Timer | `name` | Put latency |
| `blocking.queue.takes.total` | Counter | `name` | Successful take operations |
| `blocking.queue.takes.failed` | Counter | `name` | Failed take operations |
| `blocking.queue.take.duration` | Timer | `name` | Take latency |
| `blocking.queue.size` | Gauge | `name` | Queue depth |
| `executor.active` | Gauge | `name` | Active tasks |
| `executor.tasks.succeeded` | Counter | `name` | Successful tasks |
| `executor.tasks.failed` | Counter | `name` | Failed tasks |
| `executor.task.duration` | Timer | `name` | Task duration |

See the [Technical Guide](../GUIDE.md#metrics-micrometer) for details.
