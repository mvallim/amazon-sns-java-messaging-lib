# Amazon SNS Java Messaging Lib

[![Build, Publish Snapshot and Pull Request](https://github.com/mvallim/amazon-sns-java-messaging-lib/actions/workflows/cd-snapshot.yml/badge.svg)](https://github.com/mvallim/amazon-sns-java-messaging-lib/actions/workflows/cd-snapshot.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=amazon-sns-java-messaging-lib&metric=alert_status)](https://sonarcloud.io/dashboard?id=amazon-sns-java-messaging-lib)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=amazon-sns-java-messaging-lib&metric=coverage)](https://sonarcloud.io/dashboard?id=amazon-sns-java-messaging-lib)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.mvallim/amazon-sns-java-messaging-lib)](https://img.shields.io/maven-central/v/com.github.mvallim/amazon-sns-java-messaging-lib)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](http://www.apache.org/licenses/LICENSE-2.0)

The Amazon SNS Java Messaging Library provides an asynchronous, batched messaging client for Amazon SNS, supporting both AWS SDK v1 (`AmazonSNS`) and v2 (`SnsClient`). It features configurable batching with linger time, FIFO ordering, message attributes, and Micrometer metrics.

> For detailed architecture, threading model, batching behavior, and exception handling, see the [Technical Guide](GUIDE.md).

> The batch size should be chosen based on the size of individual messages and available network bandwidth as well as the observed latency and throughput improvements based on the real life load. These are configured to some sensible defaults assuming smaller message sizes and the optimal batch size for server side processing.

# Request Batch

Combine multiple requests to optimally utilise the network.

Article [Martin Fowler](https://martinfowler.com) [Request Batch](https://martinfowler.com/articles/patterns-of-distributed-systems/request-batch.html)

_**Compatible JDK 8, 11, 17, 21 and 25**_

_**Compatible AWS JDK v1 >= 1.12**_

_**Compatible AWS JDK v2 >= 2.18**_

This library supports **`Kotlin`** aswell

# 1. Quick Start

## 1.1 Prerequisite

In order to use Amazon SNS Java Messaging Lib within a Maven project, simply add the following dependency to your pom.xml. There are no other dependencies for Amazon SNS Java Messaging Lib, which means other unwanted libraries will not overwhelm your project.

You can pull it from the central Maven repositories:

#### Maven

### For AWS SDK v1

```xml
<dependency>
    <groupId>com.github.mvallim</groupId>
    <artifactId>amazon-sns-java-messaging-lib-v1</artifactId>
    <version>1.3.0</version>
</dependency>
```

### For AWS SDK v2

```xml
<dependency>
    <groupId>com.github.mvallim</groupId>
    <artifactId>amazon-sns-java-messaging-lib-v2</artifactId>
    <version>1.3.0</version>
</dependency>
```

If you want to try a snapshot version, add the following repository:

```xml
<repository>
    <id>sonatype-snapshots</id>
    <name>Sonatype Snapshots</name>
    <url>https://central.sonatype.com/repository/maven-snapshots</url>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```

#### Gradle

### For AWS SDK v1

```groovy
implementation 'com.github.mvallim:amazon-sns-java-messaging-lib-v1:1.3.0'
```

### For AWS SDK v2

```groovy
implementation 'com.github.mvallim:amazon-sns-java-messaging-lib-v2:1.3.0'
```

If you want to try a snapshot version, add the following repository:

```groovy
repositories {
    maven {
        url "https://central.sonatype.com/repository/maven-snapshots"
    }
}
```

## 1.2 Usage

### Properties `TopicProperty`

| Property              | Type        | Description                                                                    |
|-----------------------|-------------|--------------------------------------------------------------------------------|
| **`fifo`**            | **boolean** | refers if SNS is fifo or not.                                                  |
| **`maximumPoolSize`** | **int**     | refers maximum threads for producer.                                           |
| **`topicArn`**        | **string**  | refers topic arn name.                                                         |
| **`linger`**          | **int**     | refers to the time to wait before sending messages out to SNS.                 |
| **`maxBatchSize`**    | **int**     | refers to the maximum amount of data to be collected before sending the batch. |

**NOTICE**: the buffer of message store in memory is calculate using **`maximumPoolSize`** * **`maxBatchSize`** huge values demand huge memory.

#### Custom `BlockingQueue`

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(false)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(amazonSNS, topicProperty)
  .topicRequests(new LinkedBlockingQueue<>(100))
  .build();
```

#### Custom `ObjectMapper`

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(false)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(amazonSNS, topicProperty)
  .objectMapper(new ObjectMapper())
  .build();
```

#### Custom `BlockingQueue` and `ObjectMapper`

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(false)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(amazonSNS, topicProperty)
  .topicRequests(new LinkedBlockingQueue<>(100))
  .objectMapper(new ObjectMapper())
  .build();
```

#### With Micrometer metrics

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(false)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(amazonSNS, topicProperty)
  .meterRegistry(new SimpleMeterRegistry())
  .build();
```

### Standard SNS

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(false)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(amazonSNS, topicProperty).build();

final RequestEntry<MyMessage> requestEntry = RequestEntry.builder()
  .withValue(new MyMessage())
  .withMessageHeaders(Map.of())
  .build();

snsTemplate.send(requestEntry);
```

### FIFO SNS

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(true)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(amazonSNS, topicProperty).build();

final RequestEntry<MyMessage> requestEntry = RequestEntry.builder()
  .withValue(new MyMessage())
  .withMessageHeaders(Map.of())
  .withGroupId(UUID.randomUUID().toString())
  .withDeduplicationId(UUID.randomUUID().toString())
  .build();

snsTemplate.send(requestEntry);
```

### Send With Callback

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(true)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(amazonSNS, topicProperty).build();

final RequestEntry<MyMessage> requestEntry = RequestEntry.builder()
  .withValue(new MyMessage())
  .withMessageHeaders(Map.of())
  .withGroupId(UUID.randomUUID().toString())
  .withDeduplicationId(UUID.randomUUID().toString())
  .build();

snsTemplate.send(requestEntry).addCallback(
  success -> LOGGER.info("Sent: {}", success.getMessageId()),
  failure -> LOGGER.error("Failed: {} [{}]", failure.getMessage(), failure.getCode())
);

snsTemplate.send(requestEntry).addCallback(
  success -> LOGGER.info("Sent: {}", success.getMessageId())
);
```

### Send And Wait

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(true)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(amazonSNS, topicProperty).build();

final RequestEntry<MyMessage> requestEntry = RequestEntry.builder()
  .withValue(new MyMessage())
  .withMessageHeaders(Map.of())
  .withGroupId(UUID.randomUUID().toString())
  .withDeduplicationId(UUID.randomUUID().toString())
  .build();

snsTemplate.send(requestEntry).addCallback(
  success -> LOGGER.info("Sent: {}", success.getMessageId()),
  failure -> LOGGER.error("Failed: {} [{}]", failure.getMessage(), failure.getCode())
);

snsTemplate.await().join();
```

### Send And Shutdown

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(true)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> snsTemplate = AmazonSnsTemplate.builder(amazonSNS, topicProperty).build();

final RequestEntry<MyMessage> requestEntry = RequestEntry.builder()
  .withValue(new MyMessage())
  .withMessageHeaders(Map.of())
  .withGroupId(UUID.randomUUID().toString())
  .withDeduplicationId(UUID.randomUUID().toString())
  .build();

snsTemplate.send(requestEntry).addCallback(
  success -> LOGGER.info("Sent: {}", success.getMessageId()),
  failure -> LOGGER.error("Failed: {} [{}]", failure.getMessage(), failure.getCode())
);

snsTemplate.shutdown();
```

### Full Example with Builder

```java
TopicProperty topicProperty = TopicProperty.builder()
  .fifo(false)
  .linger(100L)
  .maxBatchSize(10)
  .maximumPoolSize(5)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

AmazonSnsTemplate<MyMessage> template = AmazonSnsTemplate.builder(snsClient, topicProperty)
  .meterRegistry(new SimpleMeterRegistry())
  .topicRequests(new RingBufferBlockingQueue<>(1024))
  .objectMapper(new ObjectMapper())
  .build();

template.send(RequestEntry.<MyMessage>builder()
  .withValue(new MyMessage("hello"))
  .withMessageHeaders(Map.of("source", "app-1"))
  .withGroupId(UUID.randomUUID().toString())
  .build());

template.await().thenRun(template::shutdown).join();
```

---

## Metrics

When a `MeterRegistry` is provided via the builder, the library records these Micrometer metrics:

### SNS Publish

Tags: `topic` = `<topicArn>`

| Metric                   | Type                | Description                                                |
|--------------------------|---------------------|------------------------------------------------------------|
| `sns.publish.attempts`   | Counter             | Total PublishBatch attempts                                |
| `sns.publish.success`    | Counter             | Successful messages                                        |
| `sns.publish.failure`    | Counter             | Failed messages (dynamic tags: `error_code`, `error_type`) |
| `sns.publish.duration`   | Timer               | Publish latency (p50/p95/p99)                              |
| `sns.publish.batch.size` | DistributionSummary | Messages per batch                                         |
| `sns.publish.inflight`   | Gauge               | In-flight publish batches                                  |

### Blocking Queue

Tags: `name` = `<queueName>`

| Metric                         | Type    | Description                             |
|--------------------------------|---------|-----------------------------------------|
| `blocking.queue.puts.total`    | Counter | Successful put operations               |
| `blocking.queue.puts.failed`   | Counter | Put operations that threw an exception  |
| `blocking.queue.put.duration`  | Timer   | Put latency (percentile histogram)      |
| `blocking.queue.takes.total`   | Counter | Successful take operations              |
| `blocking.queue.takes.failed`  | Counter | Take operations that threw an exception |
| `blocking.queue.take.duration` | Timer   | Take latency (percentile histogram)     |
| `blocking.queue.size`          | Gauge   | Current queue depth                     |

### Executor

Tags: `name` = `<executorName>`

| Metric                     | Type    | Description                       |
|----------------------------|---------|-----------------------------------|
| `executor.active`          | Gauge   | Tasks currently executing         |
| `executor.tasks.succeeded` | Counter | Tasks completed without exception |
| `executor.tasks.failed`    | Counter | Tasks completed with exception    |
| `executor.task.duration`   | Timer   | Task wall-clock duration          |

---

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [GitHub](https://github.com/mvallim/amazon-sns-java-messaging-lib) for versioning. For the versions available, see the [tags on this repository](https://github.com/mvallim/amazon-sns-java-messaging-lib/tags).

## Authors

* **Marcos Vallim** - _Founder, Author, Development, Test, Documentation_ - [mvallim](https://github.com/mvallim)

See also the list of [contributors](CONTRIBUTORS.txt) who participated in this project.

## License

This project is licensed under the Apache License - see the [LICENSE](LICENSE) file for details
