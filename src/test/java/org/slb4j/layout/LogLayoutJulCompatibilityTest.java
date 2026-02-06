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
package org.slb4j.layout;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.LogLayout;
import org.slb4j.MDC;
import org.slb4j.frontend.jul.JulHandler;
import org.slb4j.handler.ConsoleHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated // test changes the default locale!
@NullMarked
class LogLayoutJulCompatibilityTest {
    static final Locale systemLocale = Locale.getDefault();

    @BeforeAll
    static void setup() {
        Locale.setDefault(Locale.ROOT);
    }

    @AfterAll
    static void teardown() {
        Locale.setDefault(systemLocale);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "%4$s: %5$s%6$s%n", // Default JUL pattern
            "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s%n%4$s: %5$s%6$s%n",
            "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %3$s %5$s%6$s%n",
            "%5$s%n",
            "%1$tL %5$s%n",
            "%5$s%n",
            "%4$s %5$s%n",
            "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %5$s%n",
            "%3$s %5$s%n",
            "%2$s %5$s%n",
            "%6$s"
    })
    void testPatternCompatibility(String julPattern) throws IOException {
        // Set JUL pattern
        System.setProperty("java.util.logging.SimpleFormatter.format", julPattern);
        SimpleFormatter julFormatter = new SimpleFormatter();

        LogLayout slb4jPattern = PatternLayout.parseJulPattern(julPattern);

        // Create a LogRecord
        LogRecord logRecord = new LogRecord(Level.INFO, "Test message");
        logRecord.setLoggerName("org.slb4j.TestLogger");
        logRecord.setSourceClassName("org.slb4j.TestClass");
        logRecord.setSourceMethodName("testMethod");
        logRecord.setInstant(Instant.now());

        // 1. Get JUL output
        String julOutput = julFormatter.format(logRecord);

        // 2. Get SLB4J output
        String slb4jOutput = formatWithSlb4j(slb4jPattern, logRecord);

        System.out.println("Pattern: " + julPattern);
        System.out.print("JUL:     " + julOutput);
        System.out.print("SLB4J:   " + slb4jOutput);
        System.out.println("---");

        // 3. Compare
        assertEquals(julOutput, slb4jOutput, "Discrepancy for pattern: " + julPattern);
    }

    private static String formatWithSlb4j(LogLayout slb4jPattern, LogRecord logRecord) throws IOException {
        StringBuilder sb = new StringBuilder();
        long timestamp = logRecord.getMillis();
        String loggerName = logRecord.getLoggerName();
        LogLevel level = JulHandler.translateJulLevel(logRecord.getLevel());

        Location loc = new Location() {
            @Override public String getClassName() { return logRecord.getSourceClassName(); }

            @Override public String getMethodName() { return logRecord.getSourceMethodName(); }

            @Override public int getLineNumber() { return -1; }

            @Override public @Nullable String getFileName() { return null; }
        };

        MDC mdc = new MDC() {
            @Override public @Nullable String get(String key) { return null; }
            @Override public Map<String, String> get() { return Collections.emptyMap(); }
        };

        Throwable t = logRecord.getThrown();

        slb4jPattern.formatLogEntry(sb, timestamp, loggerName, level, null, mdc, loc,
                JulHandler.formatJulMessage(logRecord).get(),
                t, ConsoleHandler.COLOR_MAP_DEFAULT.getOrDefault(level, ConsoleCode.empty()));

        return sb.toString();
    }
}
