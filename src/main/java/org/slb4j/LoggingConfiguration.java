/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slb4j;

import org.jspecify.annotations.Nullable;
import org.slb4j.config.ConfigParser;
import org.slb4j.config.ConfigParserJul;
import org.slb4j.config.ConfigParserLog4jProperties;
import org.slb4j.filter.LoggerNamePrefixFilter;
import org.slb4j.handler.ConsoleHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * A configuration class for setting up and managing logging behaviors and settings.
 * This class provides methods and properties for configuring log handlers, log filters,
 * and other logging-related functionalities.
 */
public final class LoggingConfiguration {
    /**
     * Represents the root property key for the logging configurations.
     */
    public static final String LOGGING_ROOT = "appender";

    /**
     * Configuration key for specifying the properties of log handlers.
     * <p>
     * To configure a handler with name 'name', use {@code LOGGING_HANDLER + "name"}.
     */
    public static final String LOGGING_HANDLER = "appender";

    /**
     * A constant representing the key for specifying the handler type in configuration properties.
     */
    public static final String LOGGING_TYPE = "type";

    /**
     * Sets the root filter for the logging configuration.
     * The root filter is a special filter applied to all loggers in the hierarchy.
     *
     * @param filter the {@link LoggerNamePrefixFilter} instance to set as the root filter;
     *               determines the filtering behavior for log entries with no specific logger name;
     *               must not be null.
     */
    public void setRootFilter(LoggerNamePrefixFilter filter) {
        this.rootFilter = filter;
        filters.put("", filter);
    }

    /**
     * Retrieves the root log level of the logging configuration.
     * The root log level determines the minimum severity of log messages
     * that are processed by the root filter of the logging system.
     *
     * @return the {@link LogLevel} representing the current root log level
     *         configured in the logging system.
     */
    public LogLevel getRootLevel() {
        return rootFilter.getLevel();
    }

    /**
     * Represents different storage types for configuration files and provides methods to obtain an input stream
     * for reading configuration data based on the specified file name.
     */
    private enum ConfigFileStorage {
        /** Enum constant representing classpath storage. */
        CLASSPATH {
            @Override
            public @Nullable InputStream getInputStream(String fileName) {
                return ClassLoader.getSystemResourceAsStream(fileName);
            }
        },
        /** Enum constant representing file system storage. */
        FILE {
            @Override
            public @Nullable InputStream getInputStream(String fileName) {
                try {
                    Path p = Paths.get(fileName);
                    if (Files.exists(p)) {
                        return Files.newInputStream(p);
                    }
                } catch (InvalidPathException e) {
                    SLB4J.logInternal(LogLevel.WARN, "Invalid file path: %s", fileName);
                } catch (IOException e) {
                    SLB4J.logInternal(LogLevel.WARN, "Failed to open configuration file %s: %s", fileName, e);
                }
                return null;
            }
        };

        /**
         * Provides an input stream for reading data from a specified file.
         * <p>
         * Implementations define how the input stream is obtained based on the file location.
         *
         * @param fileName the name of the file for which the input stream is to be obtained.
         *                 Must not be null or empty.
         * @return an InputStream to read data from the specified file, or null if the file
         *         does not exist, cannot be read, or an error occurs while opening the stream.
         */
        public abstract @Nullable InputStream getInputStream(String fileName);
    }

    private record ConfigFileMeta(String fileName, ConfigFileStorage storage, Supplier<ConfigParser> parserSupplier) {
    }

    /**
     * A static mapping between configuration file paths and the corresponding suppliers
     * that provide instances of {@link ConfigParser}.
     *
     * This map is used for automatic lookup of configuration files. Entries are tried top to bottom.
     */
    private static final List<ConfigFileMeta> CONFIG_PARSERS = new CopyOnWriteArrayList<>();

    /*
     * Configure the set and order of configuration files to check for loading.
     */
    static {
        // 1. Get configuration path from System Property or Environment Variable
        String property = System.getProperty("log4j2.configurationFile");
        if (property == null) {
            property = System.getenv("LOG4J_CONFIGURATION_FILE");
        }

        // 2. Log4j2 supports "Composite Configurations" via comma-separated paths
        if (property != null) {
            for (String path : property.split(",")) {
                String trimmed = path.trim();
                // Only register if it's a property file to avoid errors on XML/JSON paths
                if (trimmed.endsWith(".properties")) {
                    CONFIG_PARSERS.add(new ConfigFileMeta(trimmed, ConfigFileStorage.FILE, ConfigParserLog4jProperties::new));
                }
            }
        }

        // 3. Default Classpath Lookups (Ordered by priority)
        // Log4j2-test always overrides log4j2 production files
        CONFIG_PARSERS.add(new ConfigFileMeta("log4j2-test.properties", ConfigFileStorage.CLASSPATH, ConfigParserLog4jProperties::new));
        CONFIG_PARSERS.add(new ConfigFileMeta("log4j2.properties", ConfigFileStorage.CLASSPATH, ConfigParserLog4jProperties::new));

        // 4. Legacy JUL Support
        CONFIG_PARSERS.add(new ConfigFileMeta("logging.properties", ConfigFileStorage.CLASSPATH, ConfigParserJul::new));
    }

    /**
     * Registers a new configuration format.
     *
     * @param extension      the file extension (e.g., "xml", "json", "yaml")
     * @param parserSupplier a supplier for the parser that can handle this format
     */
    public static void registerFormat(String extension, Supplier<ConfigParser> parserSupplier) {
        // Register for system property if it matches extension
        String property = System.getProperty("log4j2.configurationFile");
        if (property == null) {
            property = System.getenv("LOG4J_CONFIGURATION_FILE");
        }
        if (property != null) {
            for (String path : property.split(",")) {
                String trimmed = path.trim();
                if (trimmed.endsWith("." + extension)) {
                    CONFIG_PARSERS.add(0, new ConfigFileMeta(trimmed, ConfigFileStorage.FILE, parserSupplier));
                }
            }
        }

        // Add default lookups with high priority
        CONFIG_PARSERS.add(0, new ConfigFileMeta("log4j2." + extension, ConfigFileStorage.CLASSPATH, parserSupplier));
        CONFIG_PARSERS.add(0, new ConfigFileMeta("log4j2-test." + extension, ConfigFileStorage.CLASSPATH, parserSupplier));
    }

    // *** ConsoleHandler configuration ***

    /**
     * Configuration key for specifying the output stream used by the console logger.
     * <p>
     * Valid values are {@code LoggingConfiguration.SYSTEM_OUT} and
     * {@code LoggingConfiguration.SYSTEM_ERR}.
     */
    public static final String LOGGER_CONSOLE_TARGET = "target";

    /**
     * Constant representing the standard output stream to configure the console handler stream.
     */
    public static final String SYSTEM_OUT = "SYSTEM_OUT";

    /**
     * Constant representing the standard error stream to configure the console handler stream.
     */
    public static final String SYSTEM_ERR = "SYSTEM_ERR";

    /**
     * Constant representing the configuration key used to specify whether console logging
     * should include colored output for better readability.
     * <p>
     * Valid values are {@code "true"}, {@code "false"}, and {@code "auto"}.
     * <p>
     * {@code "auto"} will evaluate to {@code "true"} if the JVM is connected to a terminal,
     * otherwise {@code "false"}.
     */
    public static final String LOGGER_CONSOLE_COLORED = "colored";

    /**
     * Configuration key for specifying the pattern used by the console logger.
     */
    public static final String LOGGER_LAYOUT_TYPE = "layout.type";

    /**
     * Represents the key used for configuring the logging layout pattern in the logging configuration.
     */
    public static final String LOGGER_LAYOUT_PATTERN = "layout.pattern";

    /**
     * Constant representing colored output for the console handler.
     */
    public static final String COLOR_ENABLED = "true";

    /**
     * Constant representing non-colored output for the console handler.
     */
    public static final String COLOR_DISABLED = "false";

    /**
     * Constant representing automatic setting colored output for the console handler.
     */
    public static final String COLOR_AUTO = "auto";

    // *** FileHandler configuration ***

    /**
     * Configuration key for specifying the path to the log file.
     */
    public static final String LOGGER_FILE_NAME = "fileName";

    /**
     * Configuration key for specifying whether to append to the log file.
     */
    public static final String LOGGER_FILE_APPEND = "append";

    /**
     * Configuration key for specifying the maximum file size before rotation.
     */
    public static final String LOGGER_FILE_MAX_SIZE = "policies.size.size";

    /**
     * Configuration key for specifying the maximum number of backup files to keep.
     */
    public static final String LOGGER_FILE_MAX_BACKUPS = "strategy.max";

    /**
     * Configuration key for specifying the file pattern for archived log files.
     */
    public static final String LOGGER_FILE_PATTERN = "filePattern";

    /**
     * Configuration key for specifying the rotation time interval.
     */
    public static final String LOGGER_FILE_TIME_INTERVAL = "policies.time.interval";

    // *** filter configuration ***

    /**
     * Configuration key for specifying the properties of log filters.
     */
    public static final String LOGGING_FILTER = "filter";

    /**
     * A constant representing the logging level configuration property.
     */
    public static final String LEVEL = "level";

    // *** end of configuration constants ***

    private LogLevel statusLevel = LogLevel.WARN;
    private String statusName = "";
    private String statusDest = "err";
    private LoggerNamePrefixFilter rootFilter = new LoggerNamePrefixFilter("logger filter");
    private final LinkedHashMap<String, LogFilter> filters = new LinkedHashMap<>();
    private final LinkedHashMap<String, LogHandler> handlers = new LinkedHashMap<>();

    /**
     * Default constructor.
     */
    public LoggingConfiguration() {
        rootFilter.setLevel(LogLevel.ERROR);
        filters.put("", rootFilter);
    }

    /**
     * Retrieves an unmodifiable {@link SequencedCollection} of all registered log handlers.
     *
     * @return a sequenced collection containing all {@link LogHandler} instances currently registered
     */
    public Map<String, LogHandler> getHandlers() {
        return Collections.unmodifiableMap(handlers);
    }

    /**
     * Retrieves an unmodifiable {@link SequencedCollection} of all registered log filters.
     *
     * @return a sequenced collection containing all {@link LogFilter} instances currently registered
     */
    public SequencedCollection<LogFilter> getFilters() {
        return Collections.unmodifiableSequencedCollection(filters.sequencedValues());
    }

    /**
     * Automatically loads the logging configuration from the classpath.
     * <p>
     * The method  checks for the filenames defined in {@code CONFIG_PARSERS} and tries to
     * load and parse the corresponding properties file. If none of the files are found
     * or can be loaded, the default configuration is returned.
     *
     * @return the loaded {@code LoggingConfiguration} or {@link #defaultConfiguration()}
     *         if none could be loaded.
     */
    public static LoggingConfiguration load() {
        return CONFIG_PARSERS.stream().map(cp -> {
            SLB4J.logInternal(LogLevel.TRACE, "Trying to load %s", cp.fileName());
            try (InputStream in = cp.storage().getInputStream(cp.fileName())) {
                if (in != null) {
                    return cp.parserSupplier().get().parse(in);
                }
            } catch (IOException e) {
                SLB4J.logInternal(LogLevel.WARN, "Failed to load %s: %s", cp.fileName(), e);
            }
            return null;
        }).filter(Objects::nonNull)
        .findFirst()
        .orElseGet(LoggingConfiguration::defaultConfiguration);
    }

    /**
     * Get InputStream from file name.
     * <p>
     * If {@code fileName} is a valid file path, it will be opened as a file.
     * Otherwise, it will be treated as a resource path.
     *
     * @param fileName
     * @return the file/resource contents as an InputStream, or {@code null}, if not found or the file could
     * not be opened for reading
     */
    private static @Nullable InputStream getConfigStreamFromFileName(String fileName) {
        try {
            Path p = Paths.get(fileName);
            if (Files.exists(p)) {
                return Files.newInputStream(p);
            }
        } catch (InvalidPathException e) {
            SLB4J.logInternal(LogLevel.DEBUG, "Invalid file path, trying as resource path: %s", fileName);
        } catch (IOException e) {
            SLB4J.logInternal(LogLevel.WARN, "Failed to open configuration file %s: %s", fileName, e);
        }

        return ClassLoader.getSystemResourceAsStream(fileName);
    }

    @Override
    public String toString() {
        return "LoggingConfiguration{" +
                "handlers=" + handlers +
                ", filters=" + filters +
                '}';
    }

    /**
     * Retrieves the logger filter for the logging configuration.
     * The logger filter is responsible for controlling the logging behavior
     * based on logger name and defined log level rules.
     *
     * @return the {@link LoggerNamePrefixFilter} instance associated with the configuration,
     *         or {@code null} if no logger filter has been set.
     */
    public LoggerNamePrefixFilter getRootFilter() {
        return this.rootFilter;
    }

    /**
     * Sets the logger filter for the logging configuration.
     * The logger filter determines which log entries are allowed based on
     * the logger name prefixes and log levels.
     *
     * @param rootFilter the {@link LoggerNamePrefixFilter} instance to set as the logger filter;
     *                     can be customized to define filtering behavior, must not be null.
     */
    public void setLoggerFilter(LoggerNamePrefixFilter rootFilter) {
        this.rootFilter = rootFilter;
    }

    /**
     * Retrieves a log handler by its name.
     *
     * @param name the name of the log handler to retrieve; must not be {@code null}
     * @return the {@link LogHandler} associated with the provided name, or {@code null} if no handler is found
     */
    public @Nullable LogHandler getHandler(String name) {
        return handlers.get(name);
    }

    /**
     * Registers a new log handler or replaces an existing one in the current logging configuration.
     *
     * @param name the name of the log handler to add or replace; must be non-null and non-empty
     * @param handler the {@link LogHandler} instance to be added or replaced; must not be null
     * @return the previously registered {@link LogHandler} associated with the given name,
     *         or {@code null} if no handler was previously registered with that name
     */
    public @Nullable LogHandler addHandler(String name, LogHandler handler) {
        return handlers.put(name, handler);
    }

    /**
     * Creates a default logging configuration with predefined settings.
     * The default configuration includes a root log filter set to the INFO log level
     * and a console log handler for outputting log messages to the system output.
     *
     * @return a {@code LoggingConfiguration} instance with default filters and handlers applied
     */
    @SuppressWarnings("java:S106")
    public static LoggingConfiguration defaultConfiguration() {
        LoggingConfiguration configuration = new LoggingConfiguration();
        configuration.setRootLevel(LogLevel.INFO);
        configuration.addHandler("console", new ConsoleHandler("console", System.out, true));
        return configuration;
    }

    /**
     * Sets the root log level for the logging configuration. This determines the
     * minimum log level that will be processed by the root filter of the logging
     * system.
     *
     * @param logLevel the {@link LogLevel} to set as the root level; determines
     *                 the threshold for which log messages are allowed
     */
    public void setRootLevel(LogLevel logLevel) {
        rootFilter.setLevel(logLevel);
    }

    /**
     * Creates a copy of the provided logging configuration.
     * <p>
     * The new configuration will have its own copies of the logger filter, handlers map,
     * and filters map. Note that the log handlers themselves are NOT copied, as they
     * represent active resources (like open files or streams).
     *
     * @param other the logging configuration to copy; must not be null
     * @return a new {@code LoggingConfiguration} instance that is a copy of the provided one
     */
    public static LoggingConfiguration copyOf(LoggingConfiguration other) {
        LoggingConfiguration copy = new LoggingConfiguration();
        copy.statusLevel = other.statusLevel;
        copy.statusName = other.statusName;
        copy.statusDest = other.statusDest;
        copy.setRootFilter(other.rootFilter.copy());
        for (Map.Entry<String, LogFilter> entry : other.filters.entrySet()) {
            if (entry.getValue() != other.rootFilter) {
                copy.filters.put(entry.getKey(), entry.getValue().copy());
            }
        }
        copy.handlers.putAll(other.handlers);
        return copy;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof LoggingConfiguration other)) return false;
        return statusLevel == other.statusLevel && statusName.equals(other.statusName) && statusDest.equals(other.statusDest) && rootFilter.equals(other.rootFilter) && handlers.equals(other.handlers) && filters.equals(other.filters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusLevel, statusName, statusDest, rootFilter, handlers, filters);
    }

    /**
     * Retrieves the current logging status level for the configuration, i.e., the level to set for backend internal
     * log messages.
     *
     * @return the {@link LogLevel} representing the current logging level
     *         associated with the configuration.
     */
    public LogLevel getStatusLevel() {
        return statusLevel;
    }

    /**
     * Sets the logging status level for the configuration.
     *
     * @param statusLevel the {@link LogLevel} to set
     */
    public void setStatusLevel(LogLevel statusLevel) {
        this.statusLevel = statusLevel;
    }

    /**
     * Retrieves the name for the status logger.
     *
     * @return the status name
     */
    public String getStatusName() {
        return statusName;
    }

    /**
     * Sets the name for the status logger.
     *
     * @param statusName the status name to set
     */
    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    /**
     * Retrieves the destination for status logs.
     *
     * @return the status destination
     */
    public String getStatusDest() {
        return statusDest;
    }

    /**
     * Sets the destination for status logs.
     *
     * @param statusDest the status destination to set (err, out, or a file path)
     */
    public void setStatusDest(String statusDest) {
        this.statusDest = statusDest;
    }
}
