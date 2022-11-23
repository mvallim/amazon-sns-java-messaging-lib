# Amazon SNS Java Messaging Lib

![Java CI with Maven](https://github.com/mvallim/amazon-sns-java-messaging-lib/workflows/Java%20CI%20with%20Maven/badge.svg?branch=master)
![CodeQL](https://github.com/mvallim/amazon-sns-java-messaging-lib/workflows/CodeQL/badge.svg?branch=master)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=amazon-sns-java-messaging-lib&metric=alert_status)](https://sonarcloud.io/dashboard?id=amazon-sns-java-messaging-lib)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=amazon-sns-java-messaging-lib&metric=coverage)](https://sonarcloud.io/dashboard?id=amazon-sns-java-messaging-lib)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.mvallim/amazon-sns-java-messaging-lib/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.mvallim/amazon-sns-java-messaging-lib)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](http://www.apache.org/licenses/LICENSE-2.0)

# Request Batch

Combine multiple requests to optimally utilise the network.

Article [Martin Fowler](https://martinfowler.com) [Request Batch](https://martinfowler.com/articles/patterns-of-distributed-systems/request-batch.html)

_**Compatible JDK 8, 11, 15, 16 and 17**_

_**Compatible AWS JDK v1 >= 1.12.346**_

_**Compatible AWS JDK v2 >= 2.18.21**_

This library supports **`Kotlin`** aswell

# 1. Quick Start

## 1.1 Prerequisite

In order to use Amazon SNS Java Messaging Lib within a Maven project, simply add the following dependency to your pom.xml. There are no other dependencies for Amazon SNS Java Messaging Lib, which means other unwanted libraries will not overwhelm your project.

You can pull it from the central Maven repositories:

### For AWS SDK v1
```xml
<dependency>
    <groupId>com.github.mvallim</groupId>
    <artifactId>amazon-sns-java-messaging-lib-v1</artifactId>
    <version>1.0.0</version>
</dependency>
```

### For AWS SDK v2
```xml
<dependency>
    <groupId>com.github.mvallim</groupId>
    <artifactId>amazon-sns-java-messaging-lib-v2</artifactId>
    <version>1.0.0</version>
</dependency>
```

If you want to try a snapshot version, add the following repository:

```xml
<repository>
    <id>sonatype-snapshots</id>
    <name>Sonatype Snapshots</name>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```

#### Gradle

### For AWS SDK v1
```groovy
implementation 'com.github.mvallim:amazon-sns-java-messaging-lib-v1:1.10.0'
```

### For AWS SDK v2
```groovy
implementation 'com.github.mvallim:amazon-sns-java-messaging-lib-v2:1.10.0'
```

If you want to try a snapshot version, add the following repository:

```groovy
repositories {
    maven {
        url "https://oss.sonatype.org/content/repositories/snapshots"
    }
}
```

## 1.2 Usage

### Standard SNS

```java
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(false)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

final AmazonSnsTemplate<MyMessage> snsTemplate = new AmazonSnsTemplate<>(amazonSNS, topicProperty);

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

final AmazonSnsTemplate<MyMessage> snsTemplate = new AmazonSnsTemplate<>(amazonSNS, topicProperty);

final RequestEntry<MyMessage> requestEntry = RequestEntry.builder()
  .withValue(new MyMessage())
  .withMessageHeaders(Map.of())
  .withGroupId(UUID.randomUUID().toString())
  .withDeduplicationId(UUID.randomUUID().toString())
  .build();

snsTemplate.send(requestEntry);
```

### Send With Callback

| Interface        | Description                                         |
|------------------|-----------------------------------------------------|
| ListenableFuture | The listener interface for receiving action events. |

| Modifier and Type | Method                                                                                                                   |
|-------------------|--------------------------------------------------------------------------------------------------------------------------|
| void              | addCallback(Consumer<? super ResponseSuccessEntry> successCallback, Consumer<? super ResponseFailEntry> failureCallback) |
| void              | addCallback(Consumer<? super ResponseSuccessEntry> successCallback)                                                      |

```
final TopicProperty topicProperty = TopicProperty.builder()
  .fifo(true)
  .linger(100)
  .maxBatchSize(10)
  .maximumPoolSize(20)
  .topicArn("arn:aws:sns:us-east-2:000000000000:topic")
  .build();

final AmazonSnsTemplate<MyMessage> snsTemplate = new AmazonSnsTemplate<>(amazonSNS, topicProperty);

final RequestEntry<MyMessage> requestEntry = RequestEntry.builder()
  .withValue(new MyMessage())
  .withMessageHeaders(Map.of())
  .withGroupId(UUID.randomUUID().toString())
  .withDeduplicationId(UUID.randomUUID().toString())
  .build();

snsTemplate.send(requestEntry).addCallback(result -> {
  successCallback -> LOGGER.info("{}", successCallback), 
  failureCallback -> LOGGER.error("{}", failureCallback)
});
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [GitHub](https://github.com/mvallim/amazon-sns-java-messaging-lib) for versioning. For the versions available, see the [tags on this repository](https://github.com/mvallim/amazon-sns-java-messaging-lib/tags).

## Authors

* **Marcos Vallim** - *Founder, Author, Development, Test, Documentation* - [mvallim](https://github.com/mvallim)

See also the list of [contributors](CONTRIBUTORS.txt) who participated in this project.

## License

This project is licensed under the Apache License - see the [LICENSE](LICENSE) file for details
