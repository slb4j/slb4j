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
package org.slb4j.benchmark;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JulBenchmarkFormattingTest {
    private static final Instant TIME = Instant.parse("2026-01-02T03:04:05.006Z");

    @Test
    void compactUsesSimpleFormatterWithLoggerAfterLevel() {
        Formatter formatter = JulBenchmarkFormatting.newFormatter("COMPACT");
        String output = formatter.format(record());

        assertTrue(formatter instanceof SimpleFormatter);
        assertEquals(timestamp("%1$tH:%1$tM:%1$tS.%1$tL") + " INFO  org.slb4j.benchmark.JulBenchmark - Hello world" + System.lineSeparator(), output);

        LogRecord shortName = record();
        shortName.setLoggerName("JulBenchmark");
        assertEquals(timestamp("%1$tH:%1$tM:%1$tS.%1$tL") + " INFO  JulBenchmark                   - Hello world" + System.lineSeparator(),
                formatter.format(shortName));
    }

    @Test
    void defaultUsesSimpleFormatterWithDateLevelAndLogger() {
        Formatter formatter = JulBenchmarkFormatting.newFormatter("DEFAULT");
        String output = formatter.format(record());

        assertTrue(formatter instanceof SimpleFormatter);
        assertEquals(timestamp("%1$tF %1$tT.%1$tL")
                + " INFO  org.slb4j.benchmark.JulBenchmark - Hello world" + System.lineSeparator(), output);
    }

    @Test
    void detailedShowsSourceAndFallsBackWhenNoFrameIsAvailable() {
        String output = JulBenchmarkFormatting.newFormatter("DETAILED").format(record());

        assertEquals(timestamp("%1$tF %1$tT.%1$tL") + " [" + Thread.currentThread().getName()
                + "] INFO  (example.Caller.log(Unknown Source)) - Hello world" + System.lineSeparator(), output);
    }

    @Test
    void detailedTimestampUsesCurrentSystemTimeZoneAndMilliseconds() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            Formatter formatter = JulBenchmarkFormatting.newFormatter("DETAILED");
            LogRecord record = record();
            record.setInstant(Instant.parse("2026-03-08T06:59:59.999987654Z"));
            assertTrue(formatter.format(record).startsWith("2026-03-08 01:59:59.999 "));

            record.setInstant(Instant.parse("2026-03-08T07:00:00.001999999Z"));
            assertTrue(formatter.format(record).startsWith("2026-03-08 03:00:00.001 "));

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertTrue(formatter.format(record).startsWith("2026-03-08 07:00:00.001 "));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void detailedFindsRealCallerFileAndLineForSynchronousHandler() {
        StringBuilder output = new StringBuilder();
        Formatter formatter = JulBenchmarkFormatting.newFormatter("DETAILED");
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                output.append(formatter.format(record));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        logAtKnownLocation(logger);
        logger.removeHandler(handler);

        assertTrue(output.toString().matches("(?s).*\\(org\\.slb4j\\.benchmark\\.JulBenchmarkFormattingTest"
                + "\\.logAtKnownLocation\\(JulBenchmarkFormattingTest\\.java:[0-9]+\\)\\) - test\\R"));
    }

    @Test
    void detailedUsesTheLoggingThreadName() throws Exception {
        Formatter formatter = JulBenchmarkFormatting.newFormatter("DETAILED");
        try (var executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "jul-format-worker"))) {
            String output = executor.submit(() -> formatter.format(record())).get();
            assertTrue(output.contains(" [jul-format-worker] INFO  (example.Caller.log(Unknown Source)) - Hello world"));
        }
    }

    @Test
    void exceptionsAndMessageParametersArePreserved() {
        LogRecord record = record();
        record.setThrown(new IllegalStateException("Sample exception"));

        for (String format : new String[]{"COMPACT", "DEFAULT", "DETAILED"}) {
            String output = JulBenchmarkFormatting.newFormatter(format).format(record);
            assertTrue(output.contains(" - Hello world" + System.lineSeparator() + "java.lang.IllegalStateException: Sample exception"));
            assertTrue(output.contains("JulBenchmarkFormattingTest.exceptionsAndMessageParametersArePreserved"));
        }
    }

    @Test
    void handlesMissingLoggerAndSourceAndRejectsUnknownFormat() {
        LogRecord record = record();
        record.setLoggerName(null);
        record.setSourceClassName(null);
        record.setSourceMethodName(null);

        String detailed = JulBenchmarkFormatting.newFormatter("DETAILED").format(record);
        assertTrue(detailed.contains("(Unknown Source) - Hello world"));
        assertThrows(IllegalArgumentException.class, () -> JulBenchmarkFormatting.newFormatter("unknown"));
    }

    @Test
    void restoresSimpleFormatterPropertyAndKeepsEachPattern() {
        String property = "java.util.logging.SimpleFormatter.format";
        String previous = System.getProperty(property);
        try {
            System.clearProperty(property);
            Formatter compact = JulBenchmarkFormatting.newFormatter("COMPACT");
            assertNull(System.getProperty(property));

            System.setProperty(property, "external format%n");
            Formatter standard = JulBenchmarkFormatting.newFormatter("DEFAULT");
            assertEquals("external format%n", System.getProperty(property));
            assertTrue(compact.format(record()).startsWith(timestamp("%1$tH:%1$tM:%1$tS.%1$tL") + " INFO"));
            assertTrue(standard.format(record()).startsWith(timestamp("%1$tF %1$tT.%1$tL") + " INFO"));
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    private static LogRecord record() {
        LogRecord record = new LogRecord(Level.INFO, "Hello {0}");
        record.setInstant(TIME);
        record.setLoggerName("org.slb4j.benchmark.JulBenchmark");
        record.setSourceClassName("example.Caller");
        record.setSourceMethodName("log");
        record.setParameters(new Object[]{"world"});
        return record;
    }

    private static String timestamp(String pattern) {
        return String.format(Locale.ROOT, pattern, Date.from(TIME));
    }

    private static void logAtKnownLocation(Logger logger) {
        logger.info("test");
    }
}