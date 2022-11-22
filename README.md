# Amazon SNS Java Messaging Lib

![Java CI with Maven](https://github.com/mvallim/amazon-sns-java-messaging-lib/workflows/Java%20CI%20with%20Maven/badge.svg?branch=master)
![CodeQL](https://github.com/mvallim/amazon-sns-java-messaging-lib/workflows/CodeQL/badge.svg?branch=master)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=amazon-sns-java-messaging-lib&metric=alert_status)](https://sonarcloud.io/dashboard?id=amazon-sns-java-messaging-lib)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=amazon-sns-java-messaging-lib&metric=coverage)](https://sonarcloud.io/dashboard?id=amazon-sns-java-messaging-lib)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.mvallim/amazon-sns-java-messaging-lib/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.mvallim/amazon-sns-java-messaging-lib)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](http://www.apache.org/licenses/LICENSE-2.0)

_**Compatible JDK 8, 11, 15, 16 and 17**_

This library supports **`Kotlin`** aswell

# 1. Quick Start

## 1.1 Prerequisite

In order to use Amazon SNS Java Messaging Lib within a Maven project, simply add the following dependency to your pom.xml. There are no other dependencies for Amazon SNS Java Messaging Lib, which means other unwanted libraries will not overwhelm your project.

You can pull it from the central Maven repositories:

```xml
<dependency>
    <groupId>com.amazon.sns.messaging.lib</groupId>
    <artifactId>amazon-sns-java-messaging-lib</artifactId>
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

```groovy
implementation 'com.amazon.sns.messaging.lib:amazon-sns-java-messaging-lib:1.10.0'
```

If you want to try a snapshot version, add the following repository:

```groovy
repositories {
    maven {
        url "https://oss.sonatype.org/content/repositories/snapshots"
    }
}
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
