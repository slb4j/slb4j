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

import org.slb4j.filter.LoggerNamePrefixFilter;
import org.slb4j.handler.AbstractFileHandler;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.FileHandler;
import org.slb4j.handler.RotatingFileHandler;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SequencedCollection;
import java.util.function.Consumer;
import java.util.function.Function;
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
     * Retrieves an unmodifiable {@link SequencedCollection} of all registered log handlers.
     *
     * @return a sequenced collection containing all {@link LogHandler} instances currently registered
     */
    public SequencedCollection<LogHandler> getHandlers() {
        Collection<LogHandler> col = handlers.values(); // should always return a SequencedCollection
        return col instanceof SequencedCollection<LogHandler> sc
                ? Collections.unmodifiableSequencedCollection(sc)
                : List.copyOf(col);
    }

    /**
     * Retrieves an unmodifiable {@link SequencedCollection} of all registered log filters.
     *
     * @return a sequenced collection containing all {@link LogFilter} instances currently registered
     */
    public SequencedCollection<LogFilter> getFilters() {
        Collection<LogFilter> col = filters.values(); // should always return a SequencedCollection
        return col instanceof SequencedCollection<LogFilter> sc
                ? Collections.unmodifiableSequencedCollection(sc)
                : List.copyOf(col);
    }

    /**
     * Parses the given {@link Properties} object and creates a {@code LoggingConfiguration} instance.
     *
     * @param properties the {@link Properties} object containing the configuration settings for logging
     * @return a new {@code LoggingConfiguration} instance with settings applied from the provided properties
     */
    public static LoggingConfiguration parse(Properties properties) {
        LoggingConfiguration cfg = new LoggingConfiguration();
        cfg.configure(properties);
        return cfg;
    }

    /**
     * Parses the given {@link Properties} object into this {@code LoggingConfiguration}.
     *
     * @param properties the {@link Properties} object containing configuration settings
     */
    private void configure(Properties properties) {
        // Collect all appender names from keys like appender.<name>.type
        List<String> appenderNames = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(LOGGING_HANDLER + ".") && key.endsWith("." + LOGGING_TYPE))
                .map(key -> key.substring(LOGGING_HANDLER.length() + 1, key.length() - LOGGING_TYPE.length() - 1))
                .toList();

        appenderNames.forEach(name -> addHandler(properties, name));

        // Collect all filter names from keys like filter.<name>.level
        List<String> filterNames = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(LOGGING_FILTER + ".") && key.endsWith("." + LEVEL))
                .map(key -> key.substring(LOGGING_FILTER.length() + 1, key.length() - LEVEL.length() - 1))
                .toList();

        filterNames.forEach(name -> addFilter(properties, name));
    }

    /**
     * Processes a property from a {@link Properties} object using the provided converter, action,
     * and default value supplier.
     *
     * @param <T>          the type of the property value after conversion
     * @param properties   the {@link Properties} object containing the property to be processed
     * @param key          the key of the property to be processed
     * @param convert      a {@link Function} to convert the property value from {@link String} to the target type
     * @param action       a {@link Consumer} to process the converted value
     * @param defaultValue a {@link Supplier} to provide a default value if the property is not found or is null
     * @throws IllegalStateException if the property value is invalid or the conversion fails
     */
    private static <T> void handleProperty(Properties properties, String key, Function<String, T> convert, Consumer<T> action, Supplier<T> defaultValue) {
        String s = properties.getProperty(key);
        try {
            T value = s != null ? convert.apply(s.strip()) : defaultValue.get();
            action.accept(value);
        } catch (Exception e) {
            throw new IllegalStateException("invalid value for property " + key + ": '" + s + "'", e);
        }
    }

    /**
     * Parses a comma-separated string into a list of elements by applying a conversion
     * function to each trimmed substring.
     *
     * @param <T>     the type of elements in the resulting list
     * @param s       the comma-separated string to be parsed
     * @param convert a function that converts each trimmed substring into an element of type T
     * @return a list containing elements of type T parsed and converted from the input string
     */
    private static <T> List<T> parseList(String s, Function<String, T> convert) {
        return Arrays.stream(s.split(",")).map(String::strip).map(convert).toList();
    }

    /**
     * Adds a new log entry handler to the current logging configuration based on the given properties and handler name.
     *
     * @param properties the {@link Properties} object containing configuration for the logging handler
     * @param name the name of the handler to be added, used as a key to extract specific handler configurations
     * @throws IllegalArgumentException if an invalid handler type or configuration value is provided
     */
    private void addHandler(Properties properties, String name) {
        String prefix = LOGGING_HANDLER + "." + name + ".";

        String sType = properties.getProperty(prefix + LOGGING_TYPE, "").strip();
        LogHandler handler = switch (sType.toLowerCase()) {
            case "console" -> {
                PrintStream stream = switch (properties.getProperty(prefix + LOGGER_CONSOLE_TARGET, SYSTEM_OUT).strip()) {
                    case SYSTEM_OUT -> System.out;
                    case SYSTEM_ERR -> System.err;
                    default ->
                            throw new IllegalArgumentException("handler '" + name + "' - invalid value for '" + LOGGER_CONSOLE_TARGET + "': '" + sType + "'");
                };

                String sColored = properties.getProperty(prefix + LOGGER_CONSOLE_COLORED, COLOR_DISABLED);
                boolean colored = switch (sColored) {
                    case COLOR_ENABLED -> true;
                    case COLOR_DISABLED -> false;
                    case COLOR_AUTO -> System.console() != null && System.getenv().get("TERM") != null;
                    default ->
                            throw new IllegalArgumentException("handler '" + name + "' - invalid value for '" + prefix + LOGGER_CONSOLE_COLORED + "': '" + sColored + "'");
                };

                yield new ConsoleHandler(name, stream, colored);
            }
            case "file", "rollingfile" -> {
                String sPath = properties.getProperty(prefix + LOGGER_FILE_NAME, name + ".log").strip();
                Path path = Paths.get(sPath);
                boolean append = Boolean.parseBoolean(properties.getProperty(prefix + LOGGER_FILE_APPEND, "true").strip());
                try {
                    RotatingFileHandler fileHandler = new RotatingFileHandler(name, path, append);
                    handleProperty(properties, prefix + LOGGER_FILE_PATTERN, s -> s, fileHandler::setFilePattern, () -> null);
                    handleProperty(properties, prefix + LOGGER_FILE_MAX_SIZE, LoggingConfiguration::parseSize, fileHandler::setMaxFileSize, () -> -1L);
                    handleProperty(properties, prefix + LOGGER_FILE_MAX_BACKUPS, Integer::parseInt, fileHandler::setMaxBackupIndex, () -> 1);
                    handleProperty(properties, prefix + LOGGER_FILE_TIME_INTERVAL, s -> {
                        // For now, we only support basic time rotation if an interval is set,
                        // we'll default to ChronoUnit.HOURS if any interval is provided but no specific unit.
                        // Ideally we'd parse the unit from some other property or the filePattern.
                        return ChronoUnit.HOURS;
                    }, fileHandler::setRotationTimeUnit, () -> null);
                    yield fileHandler;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            default -> throw new IllegalArgumentException("unknown handler type for handler '" + name + "' : " + sType);
        };

        handleProperty(properties, prefix + LOGGING_FILTER, filters::get, handler::setFilter, LogFilter::allPass);
        handleProperty(properties, prefix + LOGGER_LAYOUT_PATTERN,
                LogPattern::parse, p -> {
                    if (handler instanceof ConsoleHandler consoleHandler) {
                        consoleHandler.setPattern(p);
                    } else if (handler instanceof AbstractFileHandler fileHandler) {
                        fileHandler.setPattern(p);
                    }
                },
                () -> LogPattern.DEFAULT_PATTERN
        );

        handlers.put(name, handler);
    }

    private static long parseSize(String s) {
        s = s.strip().toUpperCase();
        if (s.endsWith("MB")) {
            return Long.parseLong(s.substring(0, s.length() - 2).strip()) * 1024 * 1024;
        } else if (s.endsWith("KB")) {
            return Long.parseLong(s.substring(0, s.length() - 2).strip()) * 1024;
        } else if (s.endsWith("GB")) {
            return Long.parseLong(s.substring(0, s.length() - 2).strip()) * 1024 * 1024 * 1024;
        } else if (s.endsWith("B")) {
            return Long.parseLong(s.substring(0, s.length() - 1).strip());
        }
        return Long.parseLong(s);
    }

    @SuppressWarnings("StringConcatenationMissingWhitespace")
    private void addFilter(Properties properties, String name) {
        String prefix = LOGGING_FILTER + "." + name + ".";

        LoggerNamePrefixFilter filter = new LoggerNamePrefixFilter(name);

        // set the global filter level
        handleProperty(properties, prefix + LEVEL, LogLevel::valueOf, filter::setLevel, () -> LogLevel.INFO);
        // set the level of the root node
        handleProperty(properties, prefix + LEVEL + "rule", LogLevel::valueOf, lvl -> filter.setLevel("", lvl), () -> LogLevel.INFO);

        // set all other levels
        String filterPrefix = prefix + "rule.";
        properties.forEach((r, v) -> {
            String key = String.valueOf(r).strip();
            if (!key.startsWith(filterPrefix)) {
                return;
            }

            String rule = key.substring(filterPrefix.length()).strip();
            LogLevel level = LogLevel.valueOf(String.valueOf(v).strip());

            filter.setLevel(rule, level);
        });

        filters.put(name, filter);
    }

    /**
     * Adds the current logging configuration, including filters and handlers, to the provided {@link Properties} object.
     *
     * @param properties the {@link Properties} object to which the logging filters and handlers will be added
     */
    public void addToProperties(Properties properties) {
        // add handler configurations
        for (Map.Entry<String, LogHandler> entry : handlers.entrySet()) {
            String name = entry.getKey();
            LogHandler handler = entry.getValue();
            String prefix = LOGGING_HANDLER + "." + name + ".";

            switch (handler) {
                case ConsoleHandler consoleHandler -> {
                    properties.setProperty(prefix + LOGGING_TYPE, "Console");
                    PrintStream stream = consoleHandler.getOut();
                    String sStream = stream == System.err ? SYSTEM_ERR : SYSTEM_OUT;
                    properties.setProperty(prefix + LOGGER_CONSOLE_TARGET, sStream);
                    properties.setProperty(prefix + LOGGER_CONSOLE_COLORED, String.valueOf(consoleHandler.isColored()));
                    properties.setProperty(prefix + LOGGER_LAYOUT_TYPE, "PatternLayout");
                    properties.setProperty(prefix + LOGGER_LAYOUT_PATTERN, consoleHandler.getPattern().getPattern());
                }
                case FileHandler fileHandler -> {
                    properties.setProperty(prefix + LOGGING_TYPE, "File");
                    properties.setProperty(prefix + LOGGER_LAYOUT_TYPE, "PatternLayout");
                    properties.setProperty(prefix + LOGGER_LAYOUT_PATTERN, fileHandler.getPattern().getPattern());
                }
                case RotatingFileHandler fileHandler -> {
                    properties.setProperty(prefix + LOGGING_TYPE, fileHandler.getMaxFileSize() > 0 ? "RollingFile" : "File");
                    properties.setProperty(prefix + LOGGER_FILE_NAME, fileHandler.getPath().toString());
                    properties.setProperty(prefix + LOGGER_FILE_APPEND, String.valueOf(fileHandler.isAppend()));
                    if (fileHandler.getMaxFileSize() > 0) {
                        properties.setProperty(prefix + "policies.type", "Policies");
                        properties.setProperty(prefix + "policies.size.type", "SizeBasedTriggeringPolicy");
                        properties.setProperty(prefix + LOGGER_FILE_MAX_SIZE, String.valueOf(fileHandler.getMaxFileSize()));
                        properties.setProperty(prefix + "strategy.type", "DefaultRolloverStrategy");
                        properties.setProperty(prefix + LOGGER_FILE_MAX_BACKUPS, String.valueOf(fileHandler.getMaxBackupIndex()));
                    }
                    properties.setProperty(prefix + LOGGER_LAYOUT_TYPE, "PatternLayout");
                    properties.setProperty(prefix + LOGGER_LAYOUT_PATTERN, fileHandler.getPattern().getPattern());
                }
                default -> {
                    // do nothing
                }
            }
        }
    }

    @Override
    public String toString() {
        Properties p = new Properties();
        addToProperties(p);
        return p.toString();
    }

    public LogFilter getRootFilter() {
        return filters.getOrDefault("", LogFilter.allPass());
    }
}
