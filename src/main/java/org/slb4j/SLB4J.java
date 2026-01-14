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

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceConfigurationError;

/**
 * Utility class for logging operations.
 */
@SuppressWarnings("AccessOfSystemProperties")
public final class SLB4J {
    private SLB4J() { /* utility class */ }

    private static final LogDispatcher DISPATCHER;

    static {
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
            e.printStackTrace(System.err);
            return Optional.empty();
        }
    }

}
