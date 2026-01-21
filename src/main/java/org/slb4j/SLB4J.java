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
import org.slb4j.support.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.stream.Stream;

/**
 * Utility class for logging operations.
 */
@SuppressWarnings("AccessOfSystemProperties")
public final class SLB4J {
    private SLB4J() { /* utility class */ }

    private static final LogDispatcher DISPATCHER;

    static {
        // === check classpath pollution
        record ClassInfo(String framework, String className, String type, String description) {}

        Stream.of(
                // Backends
                new ClassInfo("log4j", "org.apache.logging.log4j.core.LoggerContext", "backend", "Log4J backend"),
                new ClassInfo("logback-classic", "ch.qos.logback.classic.Logger", "backend", "Logback classic backend"),
                new ClassInfo("logback", "ch.qos.logback.core.Appender", "backend", "Logback backend"),

                // Bridges - log4j
                new ClassInfo("log4j-slf4j-impl", "org.apache.logging.log4j.slf4j.Log4jLoggerFactory", "bridge", "Log4J to SLF4J bridge"),
                new ClassInfo("log4j-to-slf4j", "org.apache.logging.slf4j.Log4jLoggerFactory", "bridge", "Log4J to SLF4J bridge"),
                new ClassInfo("log4j-jcl", "org.apache.logging.log4j.jcl.LogFactoryImpl", "bridge", "Log4J to JCL bridge"),
                new ClassInfo("log4j-jul", "org.apache.logging.log4j.jul.LogManager", "bridge", "Log4J to JUL bridge"),

                // Bridges - slf4j
                new ClassInfo("slf4j-log4j12", "org.slf4j.impl.Log4jLoggerFactory", "bridge", "SLF4J to Log4J 1.2 bridge"),
                new ClassInfo("slf4j-jdk14", "org.slf4j.impl.JDK14LoggerFactory", "bridge", "SLF4J to JUL bridge"),
                new ClassInfo("slf4j-jcl", "org.slf4j.impl.JCLLoggerFactory", "bridge", "SLF4J to JCL bridge"),
                new ClassInfo("slf4j-simple", "org.slf4j.simple.SimpleLogger", "bridge", "SLF4J simple backend"),
                // ignore: new ClassInfo("slf4j-nop", "org.slf4j.helpers.NOPLogger", "bridge", "SLF4J NOP backend"),

                // Bridges - jcl
                new ClassInfo("jcl-over-slf4j", "org.apache.commons.logging.impl.SLF4JLogFactory", "bridge", "JCL to SLF4J bridge"),

                // Bridges - jul
                new ClassInfo("jul-to-slf4j", "org.slf4j.bridge.SLF4JBridgeHandler", "bridge", "JUL to SLF4J bridge")
        ).forEach(ci -> {
            // Warn about conflicting logging implementations on classpath
            if (Util.isClassOnClasspath(ci.className()) && !Objects.equals("slb4j", ci.framework())) {
                Util.err().format("WARNING: Classpath contains conflicting %s implementation: %s%n", ci.type(), ci.description());
            }
        });

        // === register the dispatcher
        DISPATCHER = UniversalDispatcher.getInstance();

        // === configure logging
        getLoggingProperties().ifPresent(properties ->
                {
                    LoggingConfiguration config = LoggingConfiguration.parse(properties);
                    config.getHandlers().forEach(DISPATCHER::addLogHandler);
                    DISPATCHER.setFilter(config.getRootFilter());
                }
        );

        // === wire the logging frontends

        // LOG4J
        wireLog4j();

        // SLF4J
        wireSlf4j();

        // JUL
        wireJul();

        // JCL
        wireJcl();

    }

    private static void wireJul() {
        java.util.logging.Logger root = java.util.logging.LogManager.getLogManager().getLogger("");
        // Remove existing handlers to avoid duplicates
        for (var h : root.getHandlers()) root.removeHandler(h);
        // Add your bridge
        root.addHandler(new JulHandler());
        root.setLevel(java.util.logging.Level.ALL);
    }

    private static void wireJcl() {
        System.setProperty("org.apache.commons.logging.LogFactory", "org.apache.commons.logging.impl.LogFactoryImpl");
        System.setProperty("org.apache.commons.logging.Log", "org.slb4j.frontend.jcl.LoggerJcl");
    }

    private static void wireLog4j() {
        System.setProperty("log4j2.loggerContextFactory", "org.slb4j.frontend.log4j.Log4jLoggerContextFactory");
    }

    private static void wireSlf4j() {
        System.setProperty("slf4j.provider", "org.slb4j.frontend.slf4j.LoggingServiceProviderSlf4j");
    }

    /**
     * Initializes the logging framework.
     * <p>
     * This method does nothing by itelf. But by calling it, execution of the
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
     * Loads logging configuration properties from the "logging.properties" file located in the classpath.
     * If the file is not found or an error occurs during loading, an empty {@code Optional} is returned.
     *
     * @return an {@code Optional} containing the loaded {@code Properties} if the file is found and successfully loaded,
     *         or an empty {@code Optional} if the file is not found or an error occurs.
     */
    @SuppressWarnings("OptionalContainsCollection")
    private static Optional<Properties> getLoggingProperties() {
        Properties properties = new Properties();
        try (InputStream in = ClassLoader.getSystemResourceAsStream("logging.properties")) {
            if (in == null) {
                return Optional.empty();
            } else {
                properties.load(in);
                return Optional.of(properties);
            }
        } catch (IOException e) {
            // write stacktrace to stderr because logging has not been initialized yet
            e.printStackTrace(Util.err());
            return Optional.empty();
        }
    }

}
