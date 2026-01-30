**Note: This document is still inomplete**

# SLB4J

SLB4J is a **Simple Logging Backend for Java** that comes as a single JAR without any dependencies.

- configuration using properties file or in code
- Log4J pattern syntax (applied when log4j2.properties or test-log4j2.properties is used)
- Java Util Logging pattern syntax (applied when logging.properties is used)
- Console and log file logging
- log file rotation
- filtering based on level, logger name and package name

There is also an extension package that provides UI elements for live monitoring an application's log messages.

**Java 21+ is required.**

## Supported Logging APIs

SLB4J supports the following logging APIs:

- JUL (Java Util Logging)
- Log4J2
- SLF4J
- JCL (Jakarta Commons Logging / Apache Commons Logging)

## How to Use

### Add the SLB4J Dependency

**Maven**

```xml
<dependency>
  <groupId>org.slb4j</groupId>
  <artifactId>slb4j</artifactId>
  <version>0.1-rc5</version>
</dependency>
```

**Gradle (Kotlin DSL)**

```kotlin
dependencies {
    implementation("org.slb4j:slb4j:0.1-rc5")
}
```

**Gradle (Groovy DSL)**

```groovy
dependencies {
    implementation 'org.slb4j:slb4j:0.1-rc5'
}
```

### (Only) if the main application uses JUL Logging: initialize the Library

**Note:** This is only necessary if the main application is using JUL logging since JUL does not look up the backend
implementation using SPI (Service Provider Infrastructure). If your main application uses one of the other 
logging frontends, JUL will be automatically initialized when SLB4J is loaded.

Add this static initializer to the class containing your `main` method:

```java
static {
    SLB4J.init();
}
```

### Remove Other Logging Backends and Bridges

SLB4J provides a unified logging interface, so you can remove other logging backends and bridge implementations from your project.

**Keep only the frontends you use**:

| Frontend / API          | Artifact Name   | Description                                                                                                                     |
|-------------------------|-----------------|---------------------------------------------------------------------------------------------------------------------------------|
| SLF4J                   | slf4j-api       | Simple Logging Facade for Java; provides a uniform API that can route to various logging backends.                              |
| Log4J 2 API             | log4j-api       | Apache Log4J 2's native logging API with async logging, filters, and markers.                                                  |
| java.util.logging (JUL) | (part of JDK)   | Built-in Java logging API since Java 1.4. Often bridged to SLF4J for consistent logging.                                        |
| Commons Logging (JCL)   | commons-logging | Lightweight logging facade, mostly used in older libraries; usually bridged to SLF4J today.                                     |

**Remove logging backends, bridges, and bindings** if you have them in your project:

| Title                   | Artifact Name    | Description                            |
|-------------------------|------------------|----------------------------------------|
| Log4J2 backend          | log4j-core       | Log4J2 logging backend                 |
| SLF4J → Log4J2 binding  | log4j-slf4j-impl | SLF4J binding to Log4J2 backend        |
| SLF4J → Logback backend | logback-classic  | Logging backend using SLF4J (Logback)  |
| SLF4J → console backend | slf4j-simple     | Simple SLF4J backend (console output)  |
| SLF4J → JUL binding     | slf4j-jdk14      | SLF4J binding to java.util.logging     |
| JUL → SLF4J bridge      | jul-to-slf4j     | Routes java.util.logging to SLF4J      |
| Log4J1 → SLF4J bridge   | log4j-over-slf4j | Routes Log4J 1.x to SLF4J (deprecated) |
| Log4J2 → SLF4J bridge   | log4j-to-slf4j   | Routes Log4J2 API to SLF4J             |
| JCL → SLF4J bridge      | jcl-over-slf4j   | Routes Commons Logging (JCL) to SLF4J  |

## Benchmarks

The repository contains a `run_benchmarks.py` script for benchmarking different logging
backends and frontends. The script provides a comprehensive comparison of performance
across the logging systems, including JUL, Log4J2, SLF4J, JCL, and their respective
bindings and bridges.

Run `run_benchmarks.py --help` to display the options that can be passed to the script.

Note: the  smoketest mode does not produce usable numbers; it is intended for testing
the benchmark script and that the different backends and frontends are configured
correctly.

## Status

### Done

- **Backend**
  - Console handler
  - File handler
  - Logfile rotation
- **Frontend support**
  - JUL (Java Util Logging)
  - Log4J2
  - SLF4J
  - JCL (Jakarta Commons Logging / Apache Commons Logging)
- **Log4J2 compatibility**
  - automaticially load and apply log4j2.properties / test-log4j2.properties
  - Log4J2 compatible message pattern 
    - Standard patterns
    - MDC support
    - Marker support
    - Location support
    - Locale support
  - ** Java Util Logging compatibility**
    - automaticially load and apply logging.properties
- Logging filters
- UI components for live monitoring
- Benchmarks
- Setup CI
- Publish to Maven Central
- added benchmark results under benchmark/results/${version}

### Todo

- Publish Javadoc

### Later

- JSON output format
- Read back JSON logs for later analysis

### Not Planned

#### Async logging

The file handlers already are quite performant (generally on par with Log4J and Logback or slightly faster). I have
experimented with two different async implementations but am undecided if it's worth implementing as quite some
overhead is added and will only benefit when really large amounts of messages are logged (on my system: > 500,000
messages per second).

If it is acceptable to lose some trace and debug level messages in case of a sudden system outage, you can
configure SLB4J to only flush messages with level INFO or higher. Messages below that level will then be buffered
and written out once the buffer is full or a higher-priority message triggers a flush. This can drastically
improve performance without using async logging.
