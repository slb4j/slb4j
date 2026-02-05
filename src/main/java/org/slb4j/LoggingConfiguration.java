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
import org.slb4j.config.ConfigParserLog4j;
import org.slb4j.filter.LogLevelFilter;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.support.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.SequencedCollection;
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

    private static final Map<String, Supplier<ConfigParser>> CONFIG_PARSERS = Map.of(
            "log4j2-test.properties", ConfigParserLog4j::new,
            "log4j2.properties", ConfigParserLog4j::new,
            "logging.properties", ConfigParserJul::new
    );

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

    private final LinkedHashMap<String, LogHandler> handlers = new LinkedHashMap<>();
    private final LinkedHashMap<String, LogFilter> filters = new LinkedHashMap<>();

    /**
     * Default constructor.
     */
    public LoggingConfiguration() {
        // nothing to do
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
        return CONFIG_PARSERS.entrySet().stream().map(entry -> {
                    String fileName = entry.getKey();
                    try (InputStream in = ClassLoader.getSystemResourceAsStream(fileName)) {
                        if (in != null) {
                            Properties properties = new Properties();
                            properties.load(in);
                            return entry.getValue().get().parse(properties);
                        }
                    } catch (IOException e) {
                        Util.err().println("Failed to load " + fileName + ": " + e.getMessage());
                        e.printStackTrace(Util.err());
                    }
                    return null;
                }).filter(Objects::nonNull)
                .findFirst()
                .orElseGet(LoggingConfiguration::defaultConfiguration);
    }

    @Override
    public String toString() {
        return "LoggingConfiguration{" +
                "handlers=" + handlers +
                ", filters=" + filters +
                '}';
    }

    /**
     * Retrieves the root logging filter from the current configuration.
     * If no root filter is explicitly defined, a default filter allowing all log entries will be returned.
     *
     * @return the root {@link LogFilter} instance, or {@code null} if no root filter is available
     */
    public LogFilter getRootFilter() {
        return filters.getOrDefault("", LogFilter.allPass());
    }

    /**
     * Sets the root log filter for the logging configuration.
     *
     * @param filter the {@link LogFilter} to be set as the root filter; can be null to remove the root filter
     * @return the previously set root {@link LogFilter}, or null if no filter was previously set
     */
    public @Nullable LogFilter setRootFilter(LogFilter filter) {
        return filters.put("", filter);
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
    public static LoggingConfiguration defaultConfiguration() {
        LoggingConfiguration configuration = new LoggingConfiguration();
        configuration.setRootFilter(LogLevelFilter.pass(LogLevel.INFO));
        configuration.addHandler("console", new ConsoleHandler("console", System.out, true));
        return configuration;
    }
}
