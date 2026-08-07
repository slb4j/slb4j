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

import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.frontend.jul.JulHandler;
import org.slb4j.support.TimeStampFormatter;
import org.slb4j.support.Util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Utility class for logging operations.
 */
@SuppressWarnings("java:S106") // Writing to System.err is intentional
public final class SLB4J {

    private SLB4J() { /* utility class */ }

    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

    private static final UniversalDispatcher DISPATCHER;

    private static volatile LoggingConfiguration activeConfiguration;

    private static final Map<String, String> LOADED_PLUGINS = new ConcurrentHashMap<>();

    private static final LogLevel INITIAL_STATUS_LEVEL;

    private static LogLevel statusLevel;
    private static String statusName = "SLB4J";
    private static String statusDest = "err";

    static {
        // initialize the status logger level
        INITIAL_STATUS_LEVEL = Optional.ofNullable(System.getProperty("slb4j.statusLoggerLevel", System.getProperty("SLB4J_STATUS_LOGGER_LEVEL")))
                .map(lvl -> {
                    try {
                        return LogLevel.valueOf(lvl);
                    } catch (Exception e) {
                        System.err.println("Invalid slb4j.statusLoggerLevel value: " + lvl);
                        return null;
                    }
                })
                        .or( () -> Optional.ofNullable(System.getProperty("log4j2.statusLoggerLevel", System.getProperty("LOG4J_STATUS_LOGGER_LEVEL")))
                        .map(log4jLevel -> switch (log4jLevel) {
                                    case "TRACE" -> LogLevel.TRACE;
                                    case "DEBUG" -> LogLevel.DEBUG;
                                    case "INFO" -> LogLevel.INFO;
                                    case "WARN" -> LogLevel.WARN;
                                    case "ERROR", "FATAL" -> LogLevel.ERROR;
                                    case null -> null;
                                    default -> { System.err.println("Invalid log4j2.statusLoggerLevel value: " + log4jLevel); yield null; }
                                })
                        ).orElse(null);

        statusLevel = INITIAL_STATUS_LEVEL != null ? INITIAL_STATUS_LEVEL : LogLevel.WARN;

        // === check classpath pollution
        record ClassInfo(String framework, String className, String type, String description) {}

        String bridge = "bridge";
        String backend = "backend";

        Stream.of(
                // Backends
                new ClassInfo("log4j", "org.apache.logging.log4j.core.LoggerContext", backend, "Log4J backend"),
                new ClassInfo("logback-classic", "ch.qos.logback.classic.Logger", backend, "Logback classic backend"),
                new ClassInfo("logback", "ch.qos.logback.core.Appender", backend, "Logback backend"),

                // Bridges - log4j
                new ClassInfo("log4j-slf4j-impl", "org.apache.logging.log4j.slf4j.Log4jLoggerFactory", bridge, "Log4J to SLF4J bridge"),
                new ClassInfo("log4j-to-slf4j", "org.apache.logging.slf4j.Log4jLoggerFactory", bridge, "Log4J to SLF4J bridge"),
                new ClassInfo("log4j-jcl", "org.apache.logging.log4j.jcl.LogFactoryImpl", bridge, "Log4J to JCL bridge"),
                new ClassInfo("log4j-jul", "org.apache.logging.log4j.jul.LogManager", bridge, "Log4J to JUL bridge"),

                // Bridges - slf4j
                new ClassInfo("slf4j-log4j12", "org.slf4j.impl.Log4jLoggerFactory", bridge, "SLF4J to Log4J 1.2 bridge"),
                new ClassInfo("slf4j-jdk14", "org.slf4j.impl.JDK14LoggerFactory", bridge, "SLF4J to JUL bridge"),
                new ClassInfo("slf4j-jcl", "org.slf4j.impl.JCLLoggerFactory", bridge, "SLF4J to JCL bridge"),
                new ClassInfo("slf4j-simple", "org.slf4j.simple.SimpleLogger", bridge, "SLF4J simple backend"),
                // ignore: new ClassInfo("slf4j-nop", "org.slf4j.helpers.NOPLogger", "bridge", "SLF4J NOP backend"),

                // Bridges - jcl
                new ClassInfo("jcl-over-slf4j", "org.apache.commons.logging.impl.SLF4JLogFactory", bridge, "JCL to SLF4J bridge"),

                // Bridges - jul
                new ClassInfo("jul-to-slf4j", "org.slf4j.bridge.SLF4JBridgeHandler", bridge, "JUL to SLF4J bridge")
        ).forEach(ci -> {
            // Warn about conflicting logging implementations on classpath
            if (Util.isClassOnClasspath(ci.className()) && !Objects.equals("slb4j", ci.framework())) {
                SLB4J.logInternal(LogLevel.WARN, "WARNING: Classpath contains conflicting %s implementation: %s%n", ci.type(), ci.description());
            }
        });

        // === register the dispatcher
        DISPATCHER = UniversalDispatcher.getInstance();

        // === load plugins
        loadPlugins();

        // === configure logging
        LoggingConfiguration config = null;
        try {
            config = LoggingConfiguration.load();
            SLB4J.logInternal(LogLevel.DEBUG, "Loaded logging configuration");
        } catch (RuntimeException e) {
            SLB4J.logInternal(LogLevel.WARN, "Failed to load logging configuration, using default configuration: %s", e);
            config = LoggingConfiguration.defaultConfiguration();
        }

        SLB4J.logInternal(LogLevel.TRACE, "applying logging configuration: %s", config);
        setConfiguration(config);

        // === wire the logging frontends
        wireFrontends();
    }

    private static void loadPlugins() {
        ServiceLoader.load(Plugin.class, SLB4J.class.getClassLoader()).forEach(plugin -> {
            try {
                plugin.init();
                LOADED_PLUGINS.put(plugin.name(), plugin.getClass().getName());
            } catch (Exception e) {
                SLB4J.logInternal(LogLevel.WARN, "Failed to initialize plugin %s: %s", plugin.name(), e);
            }
        });
    }

    private static void wireFrontends() {
        SLB4J.logInternal(LogLevel.TRACE, "Wiring frontends");

        // === JCL, LOG4J, and SLF4J

        // handled by SPI / in JCL: SPI-like mechanism

        // === JUL
        java.util.logging.Logger root = java.util.logging.LogManager.getLogManager().getLogger("");
        // Remove existing handlers to avoid duplicates
        for (var h : root.getHandlers()) root.removeHandler(h);
        // Add handler
        root.addHandler(new JulHandler());
        root.setLevel(java.util.logging.Level.ALL);
    }

    /**
     * Initializes the logging framework.
     * <p>
     * This method does nothing by itself. But by calling it, execution of the
     * static initializer is triggered.
     */
    public static void init() {
        SLB4J.logInternal(LogLevel.TRACE, "SLB4J initialized");
    }

    /**
     * Replaces the active configuration with the provided configuration.
     *
     * @param config the new logging configuration to be set; must not be null
     */
    public static void setConfiguration(LoggingConfiguration config) {
        setStatusLevel(config.getStatusLevel());
        setStatusName(config.getStatusName());
        setStatusDest(config.getStatusDest());

        DISPATCHER.clearLogHandlers();
        config.getHandlers().values().forEach(DISPATCHER::addLogHandler);
        DISPATCHER.setFilter(config.getRootFilter());
        DISPATCHER.setFilter(config.getRootFilter());

        activeConfiguration = LoggingConfiguration.copyOf(config);

        SLB4J.logInternal(LogLevel.INFO, "Logging configuration updated");
        SLB4J.logInternal(LogLevel.DEBUG, "Active logging configuration: %s", activeConfiguration);
    }

    /**
     * Returns the active logging configuration.
     *
     * @return the active logging configuration
     */
    public static LoggingConfiguration getConfiguration() {
        return LoggingConfiguration.copyOf(activeConfiguration);
    }

    /**
     * Returns the global LogDispatcher by using the available ILogDispatcherFactory implementations loaded
     * through ServiceLoader and connects all known loggers to it.
     *
     * @return The global LogDispatcher instance.
     * @throws ServiceConfigurationError if no factories can create a LogDispatcher.
     */
    public static LogDispatcher getDispatcher() {
        return DISPATCHER;
    }

    /**
     * Retrieves a read-only view of the currently loaded plugins.
     *
     * The map contains plugin names as keys and their respective configurations or descriptions
     * as values. The returned map is immutable, ensuring that the caller cannot modify its contents.
     *
     * @return an immutable map of loaded plugins, where the keys represent plugin names
     *         and the values represent their configurations or descriptions.
     */
    public static Map<String, String> getLoadedPlugins() {
        return Map.copyOf(LOADED_PLUGINS);
    }

    /**
     * Sets the global status logging level for the framework.
     *
     * This method updates the logging level that determines which log messages
     * will be processed and displayed. Log messages with a level below the specified
     * threshold will be ignored.
     *
     * @param level the logging level to be set. Must be a value from the {@code LogLevel} enumeration.
     */
    public static void setStatusLevel(LogLevel level) {
        if (INITIAL_STATUS_LEVEL != null) {
            SLB4J.logInternal(LogLevel.WARN, "status level set in environment, ignoring provided status level: %s", level);
            return;
        }
        statusLevel = level;
    }

    /**
     * Sets the global status logging name for the framework.
     *
     * @param name the status name to be set.
     */
    public static void setStatusName(String name) {
        statusName = name;
    }

    /**
     * Sets the global status logging destination for the framework.
     *
     * @param dest the status destination to be set (err, out, or a file path).
     */
    public static void setStatusDest(String dest) {
        statusDest = dest;
    }

    /**
     * Retrieves the current status level of the logging framework.
     *
     * The status level determines the minimum severity of log messages
     * that should be processed or displayed by the framework. Messages
     * with a severity lower than the status level may be ignored.
     *
     * @return the current status {@link LogLevel} of the logging framework.
     */
    public static LogLevel getStatusLevel() {
        return statusLevel;
    }

    /**
     * Retrieves the current status name of the logging framework.
     *
     * @return the current status name.
     */
    public static String getStatusName() {
        return statusName;
    }

    /**
     * Retrieves the current status destination of the logging framework.
     *
     * @return the current status destination.
     */
    public static String getStatusDest() {
        return statusDest;
    }

    /**
     * Logs a message at the specified log level, formatting the message with optional arguments.
     * The message will only be logged if the specified log level meets or exceeds the current
     * logging status level.
     *
     * @param level the log level at which the message should be logged. Determines whether the
     *              message will be logged based on the current status level.
     * @param msg the message to be logged. Supports formatting placeholders similar to
     *            {@link String#format(String, Object...)}.
     * @param args optional arguments to fill placeholders in the message string.
     */
    public static void logInternal(LogLevel level, String msg, Object... args) {
        if (level.ordinal() >= statusLevel.ordinal()) {
            try {
                StringBuilder sb = new StringBuilder();

                TimeStampFormatter.ISO8601_FORMATTER.appendTo(System.currentTimeMillis(), sb);

                if (!statusName.isEmpty()) {
                    sb.append(" ").append(statusName);
                }

                String caller = STACK_WALKER.walk(s -> s
                        .skip(1) // Skip SLB4J.logInternal
                        .findFirst()
                        .map(java.lang.StackWalker.StackFrame::getClassName)
                        .orElse("unknown")
                );
                sb.append(" [").append(level).append("] ").append(caller);

                sb.append(" - ").append(String.format(msg, args));

                PrintStream dest = switch (statusDest.toLowerCase()) {
                    case "out", "system_out" -> System.out;
                    case "err", "system_err" -> System.err;
                    default -> {
                        System.err.println("Setting status logger output to a file is not supported, using SYSTEM_ERR instead.");
                        yield System.err;
                    }
                };
                dest.println(sb);
            } catch (IOException e) {
                // swallowed
            }
        }
    }

    /**
     * Retrieves the root logging level.
     * <p>
     * The root logging level determines the minimum severity of log entries
     * that will be processed and displayed globally.
     *
     * @return the current root logging level as a {@link LogLevel} value.
     */
    public static LogLevel getRootLevel() {
        return getDispatcher().getRootLevel();
    }

    /**
     * Sets the global root logging level.
     * <p>
     * The root logging level determines the minimum severity of log entries
     * that will be processed and displayed globally.
     *
     * @param level the root logging level to set.
     */
    public static void setRootLevel(LogLevel level) {
        getDispatcher().setRootLevel(level);
    }

    /**
     * Sets the log level for loggers that match the specified name prefix.
     *
     * This method delegates to the global {@link LogDispatcher} to apply the log level
     * to the loggers whose names start with the given prefix.
     *
     * @param prefix the prefix of logger names for which the log level should be set.
     *               Must not be null or empty.
     * @param level  the log level to be assigned to the loggers matching the specified prefix.
     *               Must be a valid {@link LogLevel} enumeration value.
     */
    public static void setLevel(String prefix, LogLevel level) {
        getDispatcher().setLevel(prefix, level);
    }

    /**
     * Retrieves the log level associated with the specified logger name prefix.
     *
     * @param prefix the prefix of logger names for which the log level is being retrieved; must not be null or empty.
     * @return the {@link LogLevel} associated with the specified prefix, or null if no level is explicitly assigned to the prefix.
     */
    public static LogLevel getLevel(String prefix) {
        return getDispatcher().getLevel(prefix);
    }

    /**
     * Checks if the SLF4J extension for the LogBuffer is available on the classpath.
     * The method verifies whether the required class for the SLF4J extension exists in the runtime environment.
     *
     * @return {@code true} if the SLF4J LogBuffer extension is available; {@code false} otherwise.
     */
    public static boolean isSlb4jExtAvailable() {
        return Util.isClassOnClasspath("org.slf4j.ext.LogBuffer");
    }

    /**
     * Checks if the SLB4J extension for JavaFX (`org.slb4j.ext.fx.FxLogPane`) is available on the classpath.
     *
     * @return {@code true} if the class `org.slb4j.ext.fx.FxLogPane` is present on the classpath;
     *         {@code false} otherwise.
     */
    public static boolean isSlb4jExtFxAvailable() {
        return Util.isClassOnClasspath("org.slb4j.ext.fx.FxLogPane");
    }

    /**
     * Checks whether the SLB4J extension for Swing, specifically the class
     * {@code org.slb4j.ext.swing.SwingLogPane}, is available on the classpath.
     *
     * @return true if the SLB4J Swing extension is available, false otherwise.
     */
    public static boolean isSlb4jExtSwingAvailable() {
        return Util.isClassOnClasspath("org.slb4j.ext.swing.SwingLogPane");
    }
}
