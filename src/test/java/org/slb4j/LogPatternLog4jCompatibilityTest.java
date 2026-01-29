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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slb4j.frontend.log4j.LoggerLog4j;
import org.slb4j.handler.ConsoleHandler;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A test class to verify the compatibility of log patterns between Log4j and SLB4J.
 * <p>
 * This class sets up a testing environment for comparing log outputs generated
 * by Log4j and SLB4J using various logging patterns. It ensures that the formatting
 * behavior is consistent across both frameworks.
 * <p>
 * Responsibilities:
 * - Configuring Log4j with custom patterns.
 * - Capturing log events and comparing outputs between Log4j and SLB4J.
 * - Verifying compatibility by asserting that there are no discrepancies in the log outputs.
 * <p>
 * An internal `CompatibilityAppender` class is used to facilitate the collection and comparison
 * of logged outputs. Discrepancies, if any, are captured for debugging purposes.
 */
@NullMarked
class LogPatternLog4jCompatibilityTest {

    private static final String APPENDER_NAME = "TestAppender";
    private @Nullable CompatibilityAppender appender;
    private @Nullable LoggerContext context;

    @BeforeEach
    void setUp() {
        System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.core.impl.Log4jContextFactory");
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            Configurator.shutdown(context);
        }
        System.clearProperty("log4j2.loggerContextFactory");
    }

    /**
     * Configures Log4j with a specified logging pattern.
     * <p>
     * This method initializes a Log4j appender with a custom pattern that determines
     * the log message formatting. It updates the configuration of the logging context
     * and applies the new appender to all loggers.
     *
     * @param pattern the logging pattern to be used for formatting log messages
     */
    private void configureLog4j(String pattern) {
        context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();

        PatternLayout log4jLayout = PatternLayout.newBuilder()
                .withPattern(pattern)
                .withConfiguration(config)
                .build();

        LogPattern slb4jPattern = LogPattern.parseLog4jPattern(pattern);

        appender = new CompatibilityAppender(APPENDER_NAME, null, log4jLayout, slb4jPattern);
        appender.start();

        config.addAppender(appender);
        updateLoggers(config, appender);
        context.updateLoggers();
    }

    /**
     * Updates the configuration of loggers by adding a given appender and setting the logging level.
     * <p>
     * The given appender is added to the root logger of the provided configuration, and the log level
     * is set to `Level.ALL` to capture all log messages irrespective of their severity.
     *
     * @param config the logging configuration to update
     * @param appender the appender to attach to the root logger
     */
    private static void updateLoggers(Configuration config, Appender appender) {
        LoggerConfig rootConfig = config.getRootLogger();
        rootConfig.addAppender(appender, Level.ALL, null);
        rootConfig.setLevel(Level.ALL);
    }

    /**
     * Tests SLB4J formatter compatibility with Log4j formatter
     */
    @ParameterizedTest
    @ValueSource(strings = {
            // Core patterns
            "%msg%n",
            "%level %msg%n",
            "%-5level %msg%n",

            // Timestamp patterns
            "%d{yyyy-MM-dd HH:mm:ss.SSS} %msg%n",
            "%d{HH:mm:ss.SSS} %msg%n",

            // Logger name patterning
            "%logger %msg%n",
            "%logger{1} %msg%n",
            "%logger{2} %msg%n",
            "%logger{1.} %msg%n",
            "%logger{2.} %msg%n",

            // Location patterns
            "%C|'com.example.service.OrderService'",
            "%C{1}|'OrderService'",
            "%M|'processOrder'",
            "%L|'42'",
            "%F|'OrderService.java'",

            // Thread & context
            "%t %msg%n",
            "%X{userId} %msg%n",
            "%X{missing} %msg%n",

            // Marker
            "%marker %msg%n",
            "[%marker] %msg%n",

            // Escaping & literals
            "%% %msg%n",
            "\"%msg\"%n",

            // Highlight
            "%highlight{%level %msg}%n",

            // Composite
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{2} [%marker] %X{userId} - %msg%n",

            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %highlight{%-5level} %logger{36} - %msg%n",
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n",
            "%level %msg%n",
            "[%t] %logger %msg%n",
            "%d{HH:mm:ss} %-5p %c{1} - %m%n",
            "[%p] %c - %m%n",
            "%d{yyyy-MM-dd'T'HH:mm:ss,SSS} [%t] %c{2} %X{userId} - %m%n",
            "%d{ISO8601} [%t] %p %c - %m%n",
            "%d{HH:mm:ss,SSS} %-5level %logger{1} - %message%n",
            "%d{yyyyMMddHHmmss} %level %c{1.} %msg%n",
            "%p %logger %X %m%n",
            "%p %logger %X{user} %m%n",
            "%p %logger %X{missing} %m%n",
            "%-10p %10c %m%n",
            "%.5p %.10c %m%n",
            "%5.10p %-10.15c %m%n",
            "[%t] %level %logger - %msg%n%throwable",
            "[%t] %level %logger - %msg%n%ex",
            "%d{yyyy-MM-dd HH:mm:ss} %-5p %C.%M(%F:%L) - %m%n",
            "%l - %m%n",
            "Marker: %marker %msg%n",
            // predefined patterns: COMPACT
            "%highlight{%d{HH:mm:ss.SSS} %-5level %-30.30c{1.} - %msg}%n%ex",
            // predefined patterns: DEFAULT
            "%highlight{%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger - %msg}%n%ex",
            // predefined patterns: DETAILED
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %marker %logger{36} [%X] (%class.%method(%file:%line)) - %msg%n%throwable",
            // month names and 12 hour time
            "[%d{EEEE, MMMM dd, yyyy - hh:mm a}] %-5p: %m%n",
            "%d{dd-MMM hh:mm:ss a} %-5p %c{1} - %m%n",
            // patterns with locale
            "%d{dd-MMMM-yyyy}{de-DE} %p %m%n",
            "%d{EEEE, dd. MMMM yyyy}{de-DE} %p %m%n"
    })
    void testPatternCompatibility(String pattern) {
        // in CI, locale "de-DE" is not available in all runners, so ignore the pattern if the locale is not available
        assumeTrue(!pattern.contains("{de-DE}")  || Locale.forLanguageTag("de-DE").toLanguageTag().equals("de-DE"));

        configureLog4j(pattern);
        Logger logger = LogManager.getLogger("org.slb4j.TestLogger");

        org.apache.logging.log4j.ThreadContext.put("userId", "alice");
        try {
            logger.trace("Trace message");
            logger.debug("Debug message");
            logger.info("Test message");
            logger.warn("Warning message");
            logger.error("Error message");
        } finally {
            org.apache.logging.log4j.ThreadContext.clearMap();
        }

        assert appender != null;
        assertTrue(appender.getDiscrepancies().isEmpty(),
            "Discrepancies found:\n" + String.join("\n", appender.getDiscrepancies()));
    }

    /**
     * Represents an appender that compares log output between Log4j and an SLB4J.
     * <p>
     * This appender is designed for testing compatibility between the Log4j formatter and the SLB4J formatter.
     * It performs the following tasks:
     * - Captures log events and formats them using both Log4j and SLB4J.
     * - Compares the outputs from both frameworks.
     * - Records discrepancies where the formatted outputs differ.
     * <p>
     * The appender uses a provided LOG4J-like log pattern to format log events. Discrepancies can be retrieved
     * for further analysis.
     * <p>
     * Extends:
     * AbstractAppender - Provides the base functionality for custom Log4j appenders.
     * <p>
     * This class is not thread-safe as it uses a non-thread-safe collection (e.g., ArrayList) for storing discrepancies.
     */
    private static class CompatibilityAppender extends AbstractAppender {
        private final LogPattern slb4jPattern;
        private final List<String> discrepancies = new ArrayList<>();

        protected CompatibilityAppender(String name, @Nullable Filter filter, Layout<? extends Serializable> layout, LogPattern slb4jPattern) {
            super(name, filter, layout, true, null);
            this.slb4jPattern = slb4jPattern;
        }

        @Override
        public void append(LogEvent event) {
            // 1. Get Log4j output
            byte[] log4jBytes = getLayout().toByteArray(event);
            String log4jOutput = new String(log4jBytes, StandardCharsets.UTF_8);

            // 2. Get SLB4J output
            String slb4jOutput = formatWithSlb4j(event);

            // 3. Compare
            if (!log4jOutput.equals(slb4jOutput)) {
                discrepancies.add(String.format("Pattern: %s%nLog4j: [%s]%nSLB4J: [%s]",
                    ((PatternLayout)getLayout()).getConversionPattern(), log4jOutput, slb4jOutput));
            }
        }

        private String formatWithSlb4j(LogEvent event) {
            StringBuilder sb = new StringBuilder();
            
            long timestamp = event.getTimeMillis();
            String loggerName = event.getLoggerName();
            LogLevel level = LoggerLog4j.translateLog4jLevel(event.getLevel());
            String marker = event.getMarker() != null ? event.getMarker().getName() : null;
            
            MDC mdc = new MDC() {
                @Override
                public @Nullable String get(String key) {
                    return event.getContextData().getValue(key);
                }

                @Override
                public Stream<Map.Entry<String, String>> stream() {
                    return event.getContextData().toMap().entrySet().stream();
                }
            };

            LocationResolver locResolver = () -> {
                StackTraceElement ste = event.getSource();
                if (ste == null) return null;
                return new Location() {
                    @Override public String getClassName() { return ste.getClassName(); }
                    @Override public String getMethodName() { return ste.getMethodName(); }
                    @Override public int getLineNumber() { return ste.getLineNumber(); }
                    @Override public @Nullable String getFileName() { return ste.getFileName(); }
                };
            };

            try {
                slb4jPattern.formatLogEntry(sb, timestamp, loggerName, level, marker, mdc, locResolver, 
                    () -> event.getMessage().getFormattedMessage(), event.getThrown(), ConsoleHandler.COLOR_MAP_DEFAULT.getOrDefault(level, ConsoleCode.empty()));
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }

            return sb.toString();
        }

        public List<String> getDiscrepancies() {
            return discrepancies;
        }
    }
}
