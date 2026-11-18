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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

final class JulBenchmarkFormatting {

    private static final String SIMPLE_FORMATTER_PROPERTY = "java.util.logging.SimpleFormatter.format";
    private static final String COMPACT_FORMAT = "%1$tH:%1$tM:%1$tS.%1$tL %4$-5s %3$-30s - %5$s%6$s%n";
    private static final String DEFAULT_FORMAT = "%1$tF %1$tT.%1$tL %4$-5s %3$s - %5$s%6$s%n";

    private JulBenchmarkFormatting() {
    }

    static synchronized Formatter newFormatter(String format) {
        return switch (format) {
            case "COMPACT" -> newSimpleFormatter(COMPACT_FORMAT);
            case "DEFAULT" -> newSimpleFormatter(DEFAULT_FORMAT);
            case "DETAILED" -> new DetailedFormatter();
            default -> throw new IllegalArgumentException("Invalid format: " + format);
        };
    }

    private static SimpleFormatter newSimpleFormatter(String pattern) {
        String previousFormat = System.getProperty(SIMPLE_FORMATTER_PROPERTY);
        System.setProperty(SIMPLE_FORMATTER_PROPERTY, pattern);
        try {
            return new SimpleFormatter();
        } finally {
            if (previousFormat == null) {
                System.clearProperty(SIMPLE_FORMATTER_PROPERTY);
            } else {
                System.setProperty(SIMPLE_FORMATTER_PROPERTY, previousFormat);
            }
        }
    }

    private static final class DetailedFormatter extends Formatter {
        private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS", Locale.ROOT);

        @Override
        public String format(LogRecord logRecord) {
            String timestamp = TIMESTAMP_FORMAT.format(logRecord.getInstant().atZone(ZoneId.systemDefault()));
            String prefix = String.format(Locale.ROOT, "%s [%s] %-5s (%s)",
                    timestamp, Thread.currentThread().getName(), logRecord.getLevel().getLocalizedName(), location(logRecord));
            StringBuilder output = new StringBuilder(prefix).append(" - ").append(formatMessage(logRecord))
                    .append(System.lineSeparator());
            if (logRecord.getThrown() != null) {
                StringWriter trace = new StringWriter();
                logRecord.getThrown().printStackTrace(new PrintWriter(trace));
                output.append(trace);
            }
            return output.toString();
        }

        private static String location(LogRecord logRecord) {
            String sourceClass = logRecord.getSourceClassName();
            String sourceMethod = logRecord.getSourceMethodName();
            if (sourceClass == null) {
                return "Unknown Source";
            }
            String source = sourceClass + (sourceMethod == null ? "" : "." + sourceMethod);
            // These benchmarks use synchronous handlers, so the original caller is still on this thread's stack.
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                if (sourceClass.equals(frame.getClassName()) && sourceMethod != null && sourceMethod.equals(frame.getMethodName())) {
                    String file = frame.getFileName();
                    if (file != null) {
                        return source + "(" + file + (frame.getLineNumber() >= 0 ? ":" + frame.getLineNumber() : "") + ")";
                    }
                    break;
                }
            }
            return source + "(Unknown Source)";
        }
    }
}
