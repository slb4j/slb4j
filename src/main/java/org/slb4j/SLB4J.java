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
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Utility class for logging operations.
 */
public final class SLB4J {


    private SLB4J() { /* utility class */ }

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private static final UniversalDispatcher DISPATCHER;

    private static final Map<String, String> LOADED_PLUGINS = new ConcurrentHashMap<>();

    private static LogLevel statusLevel = LogLevel.WARN;

    static {
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

        // === configure logging
        LoggingConfiguration config = null;
        try {
            config = LoggingConfiguration.load();
        } catch (RuntimeException e) {
            SLB4J.logInternal(LogLevel.WARN, "Failed to load logging configuration, using default configuration: %s", e);
            config = LoggingConfiguration.defaultConfiguration();
        }

        config.getHandlers().values().forEach(DISPATCHER::addLogHandler);
        DISPATCHER.setLoggerFilter(config.getLoggerFilter());
        DISPATCHER.setFilter(config.getRootFilter());

        // === wire the logging frontends
        wireFrontends();

        // === load plugins
        loadPlugins();
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
        // nothing to do - initialization is done in the static initializer
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
        statusLevel = level;
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
    public static void logInternal(LogLevel level, String msg, Object...args) {
        if (level.ordinal() >= statusLevel.ordinal()) {
            try {
                StringBuilder sb = new StringBuilder();

                TimeStampFormatter.ISO8601_FORMATTER.appendTo(System.currentTimeMillis(), sb);

                Class<?> caller = STACK_WALKER.getCallerClass();
                sb.append(" [").append(level).append("] ").append(caller.getName());

                sb.append(" - ").append(String.format(msg, args));

                System.err.println(sb);
            } catch (IOException e) {
                // swallowed
            }
        }
    }

}
