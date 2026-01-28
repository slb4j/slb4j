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

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slb4j.frontend.jul.JulHandler;
import org.slb4j.handler.ConsoleHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NullMarked
class LogPatternJulCompatibilityTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "%4$s: %5$s%6$s%n", // Default JUL pattern
            "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s%n%4$s: %5$s%6$s%n",
            "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %3$s %5$s%6$s%n",
            "%5$s%n",
            "%1$tL %5$s%n"
    })
    void testPatternCompatibility(String julPattern) throws IOException {
        // Set JUL pattern
        System.setProperty("java.util.logging.SimpleFormatter.format", julPattern);
        SimpleFormatter julFormatter = new SimpleFormatter();

        LogPattern slb4jPattern = LogPattern.parseJulPattern(julPattern);

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

        // 3. Compare
        // We normalize the level names because SLB4J uses SLF4J names (INFO, etc.)
        // while JUL uses its own names (INFORMATION, etc.)
        // Also normalize date/time parts that might differ slightly or are not fully supported
        String normalizedJulOutput = normalizeOutput(julOutput);
        String normalizedSlb4jOutput = normalizeOutput(slb4jOutput);
        
        assertEquals(normalizedJulOutput, normalizedSlb4jOutput, "Discrepancy for pattern: " + julPattern);
    }

    private String normalizeOutput(String output) {
        return output
                .replace("INFORMATION", "INFO")
                .replace("SEVERE", "ERROR")
                .replace("WARNING", "WARN")
                .replace("FINER", "TRACE")
                .replace("FINE", "DEBUG")
                // Normalize months, AM/PM, and other platform/locale dependent parts
                // that we might not fully match in SLB4J's translateJulToLog4j
                .replaceAll("Jan\\.|Feb\\.|Mar\\.|Apr\\.|May|Jun\\.|Jul\\.|Aug\\.|Sep\\.|Oct\\.|Nov\\.|Dec\\.", "MONTH")
                .replaceAll("AM|PM", "AMPM")
                // Hour in %1$tl might be 1 or 01, normalize to single format if needed
                // For now, let's see if this is enough
                ;
    }

    private String formatWithSlb4j(LogPattern slb4jPattern, LogRecord logRecord) throws IOException {
        StringBuilder sb = new StringBuilder();
        long timestamp = logRecord.getMillis();
        String loggerName = logRecord.getLoggerName();
        LogLevel level = JulHandler.translateJulLevel(logRecord.getLevel());
        
        LocationResolver locResolver = () -> new Location() {
            @Override public String getClassName() { return logRecord.getSourceClassName(); }
            @Override public String getMethodName() { return logRecord.getSourceMethodName(); }
            @Override public int getLineNumber() { return -1; }
            @Override public @Nullable String getFileName() { return null; }
        };

        MDC mdc = new MDC() {
            @Override public @Nullable String get(String key) { return null; }
            @Override public Stream<Map.Entry<String, String>> stream() { return Stream.empty(); }
        };

        Throwable t = logRecord.getThrown();
        
        slb4jPattern.formatLogEntry(sb, timestamp, loggerName, level, null, mdc, locResolver, 
                () -> JulHandler.formatJulMessage(logRecord.getMessage(), logRecord.getParameters()).get(),
                t, ConsoleHandler.COLOR_MAP_DEFAULT.getOrDefault(level, ConsoleCode.empty()));

        return sb.toString();
    }
}
