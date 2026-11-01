# SLB4J

SLB4J is a **Simple Logging Backend for Java** that comes as a single JAR without any runtime dependencies.

- configuration using properties file or in code
- supports selected features of Log4j 2 and JUL configuration formats; see the compatibility notes below
- Log4j 2-style layouts (when using Log4j 2 configuration):
  - PatternLayout
  - SimpleLayout
  - JsonLayout
  - CsvLayout (module `slb4j-ext-layouts`)
  - XmlLayout (module `slb4j-ext-layouts`)
  - YamlLayout (module `slb4j-ext-layouts`)
- Java Util Logging pattern syntax (when logging.properties is used)
- Console and log file logging
- log file rotation
- filtering based on level, logger and/or package name
- GUI components for monitoring logs at runtime (JavaFX and Swing components)
- **Requires Java 21+**

## Supported Logging APIs

SLB4J provides adapters for the following logging APIs:

- JUL (Java Util Logging)
- Log4j 2 API
- SLF4J 2.x
- JCL (Jakarta Commons Logging / Apache Commons Logging)

The API artifacts are not bundled with `slb4j`; add the facade dependencies your application uses (for example, `slf4j-api`, `log4j-api`, or `commons-logging`). JUL is part of the JDK. The SLF4J integration currently uses a no-op MDC adapter, so calls to SLF4J's `MDC` API do not store context data.

## How to Use

### Add the SLB4J Dependency

**Maven**

```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.slb4j</groupId>
        <artifactId>slb4j-bom</artifactId>
        <version>0.14.1</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.slb4j</groupId>
      <artifactId>slb4j</artifactId>
    </dependency>
  </dependencies>
```

**Gradle (Kotlin DSL)**

```kotlin
  dependencies { 
      implementation(platform("org.slb4j:slb4j-bom:0.14.1"))
      implementation("org.slb4j:slb4j")
  }
```

**Gradle (Groovy DSL)**

```groovy
  dependencies {
      implementation platform('org.slb4j:slb4j-bom:0.14.1')
      implementation 'org.slb4j:slb4j'
  }
```

#### Optional Dependencies

Properties configuration is supported by the core artifact. To load another Log4j 2 configuration format, add the corresponding module:
- `org.slb4j:slb4j-config-xml`: XML configuration
- `org.slb4j:slb4j-config-json`: JSON configuration
- `org.slb4j:slb4j-config-yaml`: YAML configuration
- `org.slb4j:slb4j-config-all`: convenience module that includes all three format modules

Other modules available:
- `org.slb4j:slb4j-ext`: log buffer extension
- `org.slb4j:slb4j-ext-layouts`: add CSV, XML, and YAML layout support
- `org.slb4j:slb4j-ext-fx` and `org.slb4j:slb4j-ext-swing`: provide JavaFX and Swing components for displaying logging output in a window while the application is running; both support filtering by level, logger name, and text

Configuration discovery checks classpath defaults such as `log4j2-test.properties`, `log4j2.properties`, and `logging.properties`; optional format modules register corresponding `log4j2-test.*` and `log4j2.*` candidates. The loader also reads `log4j2.configurationFile` or `LOG4J_CONFIGURATION_FILE`. If multiple formats or candidate files are present, do not rely on precedence across format plugins; keep only the intended configuration candidates or verify which one is loaded. If no configuration is found, SLB4J defaults to INFO-level logging to the console on `System.out`.

### If the main application uses JUL Logging: initialize the Library

**Note:** JUL does not discover SLB4J through SPI. Call `SLB4J.init()` before the first JUL log event so the SLB4J JUL handler is installed. Other frontend providers initialize SLB4J when they are loaded, but JUL events emitted before that initialization are not routed through SLB4J; initialize it early in mixed-frontend applications as well.

Add this static initializer to the class containing your `main` method (and import `org.slb4j.SLB4J`):

```java
public final class Main {
    static {
        SLB4J.init();
    }

    public static void main(String[] args) {
        // Application entry point
    }
}
```

### Remove Other Logging Backends and Bridges

SLB4J provides a unified logging interface, so you can remove other logging backends and bridge implementations from your project.

**Keep only the frontends you use**:

| Frontend / API          | Artifact Name   | Description                                               |
|-------------------------|-----------------|-----------------------------------------------------------|
| SLF4J 2.x               | slf4j-api       | Simple Logging Facade for Java API.                       |
| Log4j 2 API             | log4j-api       | Apache Log4j 2 logging API.                               |
| java.util.logging (JUL) | (part of JDK)   | Built-in Java logging API.                                |
| Commons Logging         | commons-logging | Apache Commons Logging API / Jakarta Commons Logging API. |

**Remove competing logging backends, bindings, and bridges** if they are present in your project. Examples include:

| Title                       | Artifact Name     | Description                            |
|-----------------------------|-------------------|----------------------------------------|
| Log4J2 backend              | log4j-core        | Log4J2 logging backend                 |
| SLF4J 1.x → Log4j 2 binding | log4j-slf4j-impl  | SLF4J binding to Log4j 2 backend       |
| SLF4J 2.x → Log4j 2 binding | log4j-slf4j2-impl | SLF4J binding to Log4j 2 backend       |
| SLF4J → Logback backend     | logback-classic   | Logging backend using SLF4J (Logback)  |
| SLF4J → console backend     | slf4j-simple      | Simple SLF4J backend (console output)  |
| SLF4J → JUL binding         | slf4j-jdk14       | SLF4J binding to java.util.logging     |
| JUL → SLF4J bridge          | jul-to-slf4j      | Routes java.util.logging to SLF4J      |
| Log4J1 → SLF4J bridge       | log4j-over-slf4j  | Routes Log4J 1.x to SLF4J (deprecated) |
| Log4J2 → SLF4J bridge       | log4j-to-slf4j    | Routes Log4J2 API to SLF4J             |
| Log4J2 → JUL bridge         | log4j-to-jul      | Routes Log4J2 API to JUL               |
| JUL → Log4J2 bridge         | log4j-jul         | Routes JUL to Log4J2                   |
| JCL → Log4J2 bridge         | log4j-jcl         | Routes Commons Logging to Log4J2       |
| JCL → SLF4J bridge          | jcl-over-slf4j    | Routes Commons Logging (JCL) to SLF4J  |
| SLF4J → JCL bridge          | slf4j-jcl         | Routes SLF4J to Commons Logging        |

## Benchmarks

The repository contains a `run_benchmarks.py` script for benchmarking the SLB4J, JUL, Log4j 2, and Logback backends through the JUL, Log4j 2, SLF4J, and JCL APIs. Available combinations and run settings depend on the selected benchmark options.

Run `run_benchmarks.py --help` to display the options that can be passed to the script.

The script writes each run to `benchmark_results/<timestamp>/`. Checked-in benchmark reports are stored under `benchmark/results/<version>/`. The `smoketest` mode is a quick wiring check, not a meaningful performance measurement.

## Status

### Done

#### Backend

- Console handler
- File handler
- Logfile rotation

#### Frontend support
- JUL (Java Util Logging)
- Log4J2
- SLF4J
- JCL (Jakarta Commons Logging / Apache Commons Logging)

#### Features

- Logging filters
- UI components for live monitoring
- Benchmarks
- Setup CI
- Publish to Maven Central
- checked-in benchmark reports under `benchmark/results/<version>`

### Log4J2 compatibility

- Automatically loads classpath `log4j2.properties` / `log4j2-test.properties`; add the corresponding module for other configuration formats:
  - XML configuration: `slb4j-config-xml`
  - JSON configuration: `slb4j-config-json`
  - YAML configuration: `slb4j-config-yaml`
  - Or use `slb4j-config-all` to include all three

- Log4j 2-style message patterns (supported conversions include):
  - Common pattern conversions
  - MDC support
  - Marker support
  - Location support
  - Locale support

- Supported layouts: PatternLayout, SimpleLayout, and JsonLayout are supported by the base module, other layouts can 
  be added:
  - CsvLayout, XmlLayout, YamlLayout: slb4j-ext-layouts

- log rotation (size or time based)
  
#### Known incompatibilities with Log4J2 configuration files

  - The pattern `%d{dd-MMMM-yyyy}{de-DE} %p %m%n` does not use localized month names in Log4j 2 but does in SLB4J.
    For details, read this [comment](https://github.com/apache/logging-log4j2/issues/4177#issuecomment-4932822264)
    for the corresponding Log4J2 issue.

  - SLB4J XML configuration parsing rejects DOCTYPE declarations. This is stricter than Log4j 2 and may prevent
    loading configurations that use DTDs, including some legacy Log4j-style XML files. The restriction is
    intentional: DTD processing enables entity-related attacks such as XML External Entity (XXE) injection and
    entity-expansion denial-of-service. Use a DTD-free, self-contained Log4j 2 XML configuration instead.
  
  - SLB4J's Log4j 2 XML configuration parsing disables XInclude processing. This differs from Log4j 2, which can
    process `xi:include` elements to include configuration content from other files or URLs. The restriction is
    intentional: XInclude is a separate external-resource loading mechanism and can otherwise enable unintended
    local-file or network access. Use a single self-contained configuration file instead.
    See [Log4J2#4064](https://github.com/apache/logging-log4j2/issues/4064) for more information.

### Java Util Logging compatibility

- Automatically loads classpath `logging.properties`
- Java Util Logging pattern syntax

### Todo

- Publish hosted Javadoc
- Layouts: HTML

### Later

- Read back JSON logs for later analysis

### Not Planned

#### Async logging

The file handlers are performant in the workloads tested so far (generally on par with Log4j 2 and Logback or slightly faster); results depend on the workload and environment. I have experimented with two different async implementations and gains were marginal or non-existent. I might re-evaluate this again later.
