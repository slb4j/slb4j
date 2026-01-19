**Note: This document is still inomplete**

# SLB4J

SLB4J is a **Simple Logging Backend for Java** that comes as a single JAR without any dependencies.

- Log4J pattern syntax
- Logging to console or log files
- log rotation
- filtering
- configuration using a properties file or in code

There is also an extension package that provides UI elements for live monitoring an application's log messages.

Java 21+ is required.

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
TODO
```

**Gradle (Kotlin DSL)**

```kotlin
TODO
```

**Gradle (Groovy DSL)**

```groovy
TODO
```

### Initialize the Library

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

**Remove backends, bridges, and bindings** if you have them in your project:

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
- **Log4J2 compatible message pattern**
  - Standard patterns
  - MDC support
  - Marker support
  - Location support
- `logging.properties` file
- Filters
- UI components for live monitoring
- Benchmarks
- Setup CI

### Todo

- Publish to Maven Central
- Publish Javadoc
- Add benchmark results

### Later

- JSON output format
- Read back JSON logs for later analysis

### Not Planned

#### Async logging

The file handlers already use **pre-allocated buffers** to reduce lock contention in multi-threaded 
environments. Even async loggers will hit a throughput limit determined by the I/O system and must either block or
drop messages.

You can configure SLB4J to flush only messages with level INFO or higher. Messages below that level are buffered
and written once the buffer is full or a high-priority message triggers a flush.

