# SLB4J Configuration Properties

This document describes the Log4J 2 configuration properties that SLB4J aims to support.

## Log4J Properties Compatibility

| Property Key                               | Description                                                                            | SLB4J Support       |
|--------------------------------------------|----------------------------------------------------------------------------------------|---------------------|
| `status`                                   | The level of internal Log4j events that should be logged to the console.               | No                  |
| `dest`                                     | Either "err" for stderr, "out" for stdout, a file path, or a URL.                      | No                  |
| `shutdownHook`                             | Specifies whether or not Log4j should automatically shut down when the JVM shuts down. | No                  |
| `shutdownTimeout`                          | Specifies how many milliseconds the shutdown hook should wait before terminating.      | No                  |
| `monitorInterval`                          | The interval in seconds to check for configuration changes.                            | No                  |
| `name`                                     | The name of the configuration.                                                         | No                  |
| `packages`                                 | A comma-separated list of packages to search for plugins.                              | No                  |
| `verbose`                                  | Enables diagnostic information while loading plugins.                                  | No                  |
| `dest`                                     | Specifies the output destination for status messages.                                  | No                  |
| `appender.<name>.type`                     | The type of the appender (e.g., Console, File, RollingFile).                           | Yes                 |
| `appender.<name>.name`                     | The name of the appender.                                                              | Yes                 |
| `appender.<name>.target`                   | The target for the output of a Console appender.                                       | Yes                 |
| `appender.<name>.fileName`                 | The path to the file to write logs to for a File appender.                             | Yes                 |
| `appender.<name>.filePattern`              | The pattern of the file name of the archived log file for a RollingFile appender.      | Yes                 |
| `appender.<name>.append`                   | Whether to append to the file or overwrite it for a File appender.                     | Yes                 |
| `appender.<name>.policies.type`            | The type of policies for a RollingFile appender (e.g., Policies).                      | Yes (implied)       |
| `appender.<name>.policies.size.type`       | The type of size based triggering policy (SizeBasedTriggeringPolicy).                  | Yes (implied)       |
| `appender.<name>.policies.size.size`       | The size at which to trigger a rollover (e.g., 10 MB).                                 | Yes                 |
| `appender.<name>.policies.time.type`       | The type of time based triggering policy (TimeBasedTriggeringPolicy).                  | Yes (implied)       |
| `appender.<name>.policies.time.interval`   | The interval at which to trigger a rollover.                                           | Yes (basic)         |
| `appender.<name>.strategy.type`            | The type of rollover strategy (e.g., DefaultRolloverStrategy).                         | Yes (implied)       |
| `appender.<name>.strategy.max`             | The maximum number of backup files to keep.                                            | Yes                 |
| `appender.<name>.layout.type`              | The type of layout (e.g., PatternLayout, JSONLayout).                                  | Yes (PatternLayout) |
| `appender.<name>.layout.pattern`           | The log pattern for PatternLayout.                                                     | Yes                 |
| `appender.<name>.filter.<type>.type`       | The type of filter (e.g., ThresholdFilter, MarkerFilter).                              | No                  |
| `appender.<name>.filter.<type>.onMatch`    | Action to take on match (ACCEPT, DENY, NEUTRAL).                                       | No                  |
| `appender.<name>.filter.<type>.onMismatch` | Action to take on mismatch (ACCEPT, DENY, NEUTRAL).                                    | No                  |
| `appender.<name>.filter.<type>.level`      | The level to match for a ThresholdFilter.                                              | No                  |
| `logger.<name>.name`                       | The name (package/class) of the logger.                                                | No                  |
| `logger.<name>.level`                      | The logging level for the specified logger.                                            | No                  |
| `logger.<name>.appenderRef.<ref>.ref`      | Reference to an appender by name.                                                      | No                  |
| `logger.<name>.additivity`                 | Whether to propagate log events to parent loggers.                                     | No                  |
| `rootLogger.level`                         | The logging level for the root logger.                                                 | No                  |
| `rootLogger.appenderRef.<ref>.ref`         | Reference to an appender for the root logger.                                          | No                  |
| `filter.<name>.level`                      | The threshold level for a filter.                                                      | Yes                 |

## Property Explanations

### Global Properties

* **`status`**: Sets the level of Log4j's internal status messages. Possible values: `trace`, `debug`, `info`, `warn`,
  `error`, `fatal`.
* **`monitorInterval`**: If set to a non-zero value, Log4j will check the configuration file for changes every
  `monitorInterval` seconds.
* **`name`**: An optional name for the configuration.
* **`shutdownHook`**: Controls whether Log4j registers a JVM shutdown hook. Values: `enable` or `disable`.
* **`shutdownTimeout`**: Timeout in milliseconds for the shutdown hook.

### Appenders

Appenders are responsible for delivering LogEvents to their destination.

* **`appender.<name>.type`**: Specifies the implementation class or plugin name of the appender. Common values:
  `Console`, `File`, `RollingFile`.
* **`appender.<name>.name`**: A unique identifier for the appender instance.

#### Console Appender

* **`appender.<name>.target`**: The target for the output. Values: `SYSTEM_OUT` or `SYSTEM_ERR`.

#### File Appender

* **`appender.<name>.fileName`**: The path to the file to write logs to.
* **`appender.<name>.append`**: Whether to append to the file or overwrite it. Values: `true` or `false`.

#### RollingFile Appender

* **`appender.<name>.fileName`**: The path to the file to write logs to.
* **`appender.<name>.filePattern`**: The pattern of the file name of the archived log file.
* **`appender.<name>.append`**: Whether to append to the file or overwrite it.
* **`appender.<name>.policies.type`**: Set to `Policies`.
* **`appender.<name>.policies.size.type`**: Set to `SizeBasedTriggeringPolicy`.
* **`appender.<name>.policies.size.size`**: The size at which to trigger a rollover (e.g., `10 MB`, `500 KB`).
* **`appender.<name>.policies.time.type`**: Set to `TimeBasedTriggeringPolicy`.
* **`appender.<name>.policies.time.interval`**: The interval at which to trigger a rollover based on the most specific
  time unit in the `filePattern`.
* **`appender.<name>.strategy.type`**: Set to `DefaultRolloverStrategy`.
* **`appender.<name>.strategy.max`**: The maximum number of backup files to keep.

#### Layouts

* **`appender.<name>.layout.type`**: Defines how the log event is formatted. Most commonly `PatternLayout`.
* **`appender.<name>.layout.pattern`**: For `PatternLayout`, defines the conversion pattern (e.g.,
  `%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n`).

### Loggers

Loggers are the objects that capture logging information.

* **`logger.<name>.name`**: The logger name, usually the fully qualified class name or package name.
* **`logger.<name>.level`**: The threshold level for this logger.
* **`logger.<name>.additivity`**: If `true` (default), the log event will also be sent to the parent logger's appenders.
* **`logger.<name>.appenderRef.<ref>.ref`**: The name of an appender to associate with this logger.

### Root Logger

The root logger is the parent of all other loggers.

* **`rootLogger.level`**: The default logging level for the entire application.
* **`rootLogger.appenderRef.<ref>.ref`**: The name of an appender to associate with the root logger.

### Filters

Filters can be applied at various levels (global, appender, logger).

* **`filter.<type>.type`**: The type of filter, e.g., `ThresholdFilter`.
* **`filter.<type>.level`**: For `ThresholdFilter`, the level to match.
* **`filter.<type>.onMatch`**: What to do if the filter matches. Values: `ACCEPT`, `DENY`, `NEUTRAL`.
* **`filter.<type>.onMismatch`**: What to do if the filter does not match. Values: `ACCEPT`, `DENY`, `NEUTRAL`.
