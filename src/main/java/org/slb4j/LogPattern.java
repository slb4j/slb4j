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

import org.slb4j.support.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The LogPattern class handles the formatting of log entries using Log4J-style format strings.
 */
public final class LogPattern {

    private static final String NEWLINE = System.lineSeparator();
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    private static final Pattern PATTERN = Pattern.compile("%(-?\\d*)(\\.\\d+)?([a-zA-Z]+)(\\{([^}]+)})?|%%");

    /**
     * The default pattern used for log formatting.
     * <p>
     * Example output:
     * <pre>
     * 2026-01-11 15:19:09.532 TRACE com.example.Application - Message from JUL
     * 2026-01-11 15:19:09.540 DEBUG com.example.Application - Message from JCL
     * 2026-01-11 15:19:09.568 WARN  com.example.Application - Message from Log4j
     * 2026-01-11 15:19:09.573 INFO  com.example.Application - Message from SLF4J
     * </pre>
     */
    public static final LogPattern DEFAULT_PATTERN = parse("%Cstart%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger - %msg%Cend%n");

    /**
     * A compact log pattern used to format log entries in a concise and structured manner.
     * The pattern defines the format of log messages by specifying placeholders, alignment,
     * truncation, and other layout options.
     * <p>
     * Pattern description:
     * <ul>
     * <li>`%Cstart` and `%Cend`: Markers for console color codes (if supported by the logging system).
     * <li>`%d{HH:mm:ss.SSS}`: The timestamp of the log entry without the date.
     * <li>`%-5level`: The log level, left-aligned with a width of 5 characters.
     * <li>`%-30.30c{1.}`: The logger name, left-aligned and truncated to a maximum of 30 characters, showing only the first fragment of the name.
     * <li>`%msg`: The log message.
     * <li>`%n`: A new line character.
     * </ul>
     * Use when a compact and human-readable log format is preferred, such as console-based logging.
     */
    public static final LogPattern COMPACT_PATTERN = parse("%Cstart%d{HH:mm:ss.SSS} %-5level %-30.30c{1.} - %msg%Cend%n");

    /**
     * Defines an interface for formatting log entries in a customizable and extensible manner.
     * Implementations of this interface allow specific components of a log entry to be
     * processed and appended to a {@link Appendable} in a format defined by the implementing class.
     */
    public interface LogPatternEntry {
        /**
         * Formats a log entry by appending its components to the given {@link Appendable} instance.
         * This method is responsible for processing and serializing the provided log data into a custom
         * format defined by the implementing class of the interface.
         *
         * @param app the {@link Appendable} instance to which the formatted log entry will be appended
         * @param instant the {@link Instant} representing the timestamp of the log event
         * @param loggerName the name of the logger that generated the log event
         * @param lvl the {@link LogLevel} representing the severity level of the log event
         * @param mrk an optional marker for the log entry, or null if not provided
         * @param mdc an optional {@link MDC} containing diagnostic context data, or null if not provided
         * @param location an optional {@link Location} detailing the source of the log event, or null if unknown
         * @param msg a {@link Supplier} providing the log message, or null if not available
         * @param t an optional {@link Throwable} associated with the log event, or null if no exception occurred
         * @param consoleCodes an optional {@link ConsoleCode} defining console-specific format codes, or null if not used
         * @throws IOException if an I/O error occurs while appending to the {@link Appendable} instance
         */
        void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException;

        /**
         * Retrieves the Log4j-compatible pattern string used for formatting log entries.
         * The pattern defines how various components of a log entry, such as the timestamp,
         * log level, logger name, and message, are represented in the output.
         *
         * @return a string representing the Log4j-compatible pattern for formatting log entries.
         */
        String getLog4jPattern();

        /**
         * Determines whether the location information (e.g., source file, line number)
         * is required for logging.
         *
         * @return a boolean value indicating whether location information is needed.
         */
        default boolean isLocationNeeded() {
            return false;
        }
    }

    /**
     * An abstract representation of a log format entry, which specifies how individual
     * components of a log message are formatted. This class implements the {@link LogPatternEntry}
     * interface and provides foundational methods for formatting and alignment.
     * <p>
     * Subclasses must define the specific formatting behavior for different log components.
     */
    public abstract static class AbstractLogPatternEntry implements LogPatternEntry {
        protected final String prefix;
        protected final int minWidth;
        protected final int maxWidth;
        protected final boolean leftAlign;
        private final boolean locationNeeded;

        /**
         * Constructs an instance of the AbstractLogPatternEntry with the specified
         * formatting parameters.
         *
         * @param prefix The prefix string that identifies the log component. This
         *               string will be included in the formatted log output.
         * @param minWidth The minimum width of the formatted output. If the formatted
         *                 log component is shorter than this width, padding will be
         *                 added to meet the minimum length.
         * @param maxWidth The maximum width of the formatted output. If the formatted
         *                 log component is longer than this width, it will be truncated
         *                 to the specified maximum length.
         * @param leftAlign A flag indicating whether the formatted output should be
         *                  left-aligned. If true, padding will be added to the right
         *                  of the formatted output; otherwise, padding will be added
         *                  to the left.
         * @param locationNeeded A flag indicating whether this entry requires location information.
         */
        protected AbstractLogPatternEntry(String prefix, int minWidth, int maxWidth, boolean leftAlign, boolean locationNeeded) {
            this.prefix = prefix;
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
            this.leftAlign = leftAlign;
            this.locationNeeded = locationNeeded;
        }

        private static final int N_SPACES = 20;
        private static final String SPACES = " ".repeat(N_SPACES);

        @Override
        public String toString() {
            return getLog4jPattern();
        }

        @Override
        public boolean isLocationNeeded() {
            return locationNeeded;
        }

        /**
         * Appends a specified number of spaces to the given Appendable.
         *
         * @param app the Appendable to which spaces will be appended
         * @param n the number of spaces to append
         */
        private static void appendSpaces(Appendable app, int n) throws IOException {
            while (n > 0) {
                int count = Math.min(n, SPACES.length());
                app.append(SPACES, 0, count);
                n -= count;
            }
        }

        /**
         * Appends a formatted string value to the provided {@code Appendable}. The method applies
         * formatting rules such as truncation and alignment based on the class fields {@code minWidth},
         * {@code maxWidth}, and {@code leftAlign}. It delegates to another version of the same method,
         * enabling additional control over left truncation.
         *
         * @param app the {@code Appendable} to which the formatted value will be appended
         * @param value the string value to format and append; if {@code null}, it will be treated as an empty string
         */
        protected void appendFormatted(Appendable app, @Nullable CharSequence value) throws IOException {
            appendFormatted(app, value, false);
        }

        /**
         * Appends a formatted string value to the provided {@code Appendable}, applying
         * formatting rules such as truncation, padding, and alignment based on the
         * specified class fields {@code minWidth}, {@code maxWidth}, and {@code leftAlign}.
         * This method provides additional control over truncation direction.
         * <p>
         * If the string value exceeds the maximum width, it will either be truncated
         * from the left or the right, depending on the {@code leftTruncate} parameter.
         * If the string value is shorter than the minimum width, padding will be added
         * on either the left or right side, as determined by the alignment settings.
         *
         * @param app the {@code Appendable} to which the formatted value will be appended
         * @param value the string value to format and append; if {@code null}, it will be treated as an empty string
         * @param leftTruncate a flag indicating whether to truncate the string from the left when its length exceeds the maximum width
         */
        protected void appendFormatted(Appendable app, @Nullable CharSequence value, boolean leftTruncate) throws IOException {
            if (value == null) {
                value = "";
            }

            if (maxWidth > 0 && value.length() > maxWidth) {
                if (leftTruncate) {
                    app.append(value, value.length() - maxWidth, value.length());
                } else {
                    app.append(value, 0, maxWidth);
                }
                return;
            }

            if (value.length() < minWidth) {
                int padding = minWidth - value.length();
                if (leftAlign) {
                    app.append(value);
                    appendSpaces(app, padding);
                } else {
                    appendSpaces(app, padding);
                    app.append(value);
                }
            } else {
                app.append(value);
            }
        }

        @Override
        public String getLog4jPattern() {
            if (minWidth == 0 && maxWidth == 0) {
                return "%" + prefix;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("%");
            if (leftAlign) sb.append("-");
            if (minWidth > 0) sb.append(minWidth);
            if (maxWidth > 0) sb.append(".").append(maxWidth);
            sb.append(prefix);
            return sb.toString();
        }
    }

    /**
     * Represents a literal string entry in a log format.
     */
    public static class LiteralEntry implements LogPatternEntry {
        private final String literal;

        /**
         * Creates a new instance of LiteralEntry with the specified literal.
         *
         * @param literal the fixed string that this entry represents;
         *                it will be appended during log formatting.
         */
        public LiteralEntry(String literal) {
            this.literal = literal;
        }

        @Override
        public String toString() {
            return getLog4jPattern();
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            app.append(literal);
        }

        @Override
        public String getLog4jPattern() {
            return literal.replace("%", "%%");
        }
    }

    /**
     * Represents a specific log format entry for handling log levels within log messages.
     * <p>
     * Instances of this class are responsible for converting a {@code LogLevel} to a string
     * and applying formatting options such as alignment and truncation based on the
     * parent class's configuration.
     */
    public static class LevelEntry extends AbstractLogPatternEntry {
        /**
         * Constructs a LevelEntry instance with the specified formatting configuration.
         *
         * @param minWidth The minimum width of the formatted log level string. If the formatted
         *                 output is shorter than this width, padding will be added.
         * @param maxWidth The maximum width of the formatted log level string. If the formatted
         *                 output is longer than this width, it will be truncated.
         * @param leftAlign A flag indicating whether the log level string should be left-aligned.
         *                  If true, padding will be added to the right of the string; otherwise,
         *                  padding will be added to the left.
         */
        public LevelEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("p", minWidth, maxWidth, leftAlign, false);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, lvl.name());
        }
    }

    private static String abbreviate(String name, int abbreviationLength) {
        if (abbreviationLength <= 0) {
            return name;
        }
        String[] parts = name.split("\\.");
        if (parts.length <= abbreviationLength) {
            return name;
        }
        StringBuilder abbreviated = new StringBuilder();
        for (int i = parts.length - abbreviationLength; i < parts.length; i++) {
            if (!abbreviated.isEmpty()) {
                abbreviated.append('.');
            }
            abbreviated.append(parts[i]);
        }
        return abbreviated.toString();
    }

    /**
     * A specialized log format entry for formatting and appending logger names to log messages.
     * <p>
     * Instances of this class are responsible for appending the logger name to a {@code Appendable}
     * with formatting applied according to the specified minimum width, maximum width, and alignment
     * settings. If truncation is required, the logger name will be truncated from the left.
     */
    public static class LoggerEntry extends AbstractLogPatternEntry {
        private final int abbreviationLength;

        /**
         * Constructs an instance of LoggerEntry, a specialized log format entry
         * for formatting and appending logger names to log messages.
         *
         * @param minWidth the minimum width of the logger name in the formatted output.
         *                 If the logger name is shorter than this width, padding will
         *                 be added to meet the minimum length.
         * @param maxWidth the maximum width of the logger name in the formatted output.
         *                 If the logger name exceeds this width, it will be truncated
         *                 from the left to meet the specified maximum length.
         * @param leftAlign a flag indicating whether the logger name should be left-aligned.
         *                  If true, padding will be added to the right of the logger name;
         *                  otherwise, padding will be added to the left.
         * @param abbreviationLength the number of rightmost components of the logger name to keep.
         */
        public LoggerEntry(int minWidth, int maxWidth, boolean leftAlign, int abbreviationLength) {
            super("c", minWidth, maxWidth, leftAlign, false);
            this.abbreviationLength = abbreviationLength;
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, abbreviate(loggerName, abbreviationLength), true);
        }

        @Override
        public String getLog4jPattern() {
            String format = super.getLog4jPattern();
            if (abbreviationLength > 0) {
                format = format.replace(prefix, prefix + "{" + abbreviationLength + "}");
            }
            return format;
        }
    }

    /**
     * Represents a log format entry for the thread name.
     */
    public static class ThreadEntry extends AbstractLogPatternEntry {
        /**
         * Constructs a new ThreadEntry instance, representing a log format entry for the thread name.
         *
         * @param minWidth The minimum width of the formatted thread name. If the formatted
         *                 thread name is shorter than this width, padding will be added to meet
         *                 the minimum length.
         * @param maxWidth The maximum width of the formatted thread name. If the formatted
         *                 thread name is longer than this width, it will be truncated to the
         *                 specified maximum length.
         * @param leftAlign A flag indicating whether the formatted thread name should be
         *                  left-aligned. If true, padding will be added to the right of the
         *                  formatted thread name; otherwise, padding will be added to the left.
         */
        public ThreadEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("t", minWidth, maxWidth, leftAlign, false);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, Thread.currentThread().getName());
        }
    }

    /**
     * Represents a log format entry for the MDC (Mapped Diagnostic Context).
     */
    public static class MdcEntry extends AbstractLogPatternEntry {
        private final @Nullable String key;

        /**
         * Constructs an instance of MdcEntry, which represents a log format entry
         * for the Mapped Diagnostic Context (MDC). This is used to format and include
         * contextual information captured in the MDC in log statements.
         *
         * @param minWidth The minimum width of the formatted output. If the formatted
         *                 MDC entry is shorter than this width, padding will be added
         *                 to meet the minimum length.
         * @param maxWidth The maximum width of the formatted output. If the formatted
         *                 MDC entry is longer than this width, it will be truncated
         *                 to the specified maximum length.
         * @param leftAlign A flag indicating whether the formatted output should be
         *                  left-aligned. If true, padding will be added to the right
         *                  of the formatted output; otherwise, padding will be added
         *                  to the left.
         * @param key The specific key from the MDC whose value should be formatted
         *            and included in the log output. If null, the entire MDC will
         *            be formatted as a key-value string.
         */
        public MdcEntry(int minWidth, int maxWidth, boolean leftAlign, @Nullable String key) {
            super("X", minWidth, maxWidth, leftAlign, false);
            this.key = key;
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            if (mdc == null) {
                return;
            }
            if (key != null) {
                appendFormatted(app, mdc.get(key));
            } else {
                StringBuilder mdcSb = new StringBuilder();
                mdc.stream().forEach(e -> {
                    if (!mdcSb.isEmpty()) {
                        mdcSb.append(", ");
                    }
                    mdcSb.append(e.getKey()).append("=").append(e.getValue());
                });
                appendFormatted(app, mdcSb.toString());
            }
        }

        @Override
        public String getLog4jPattern() {
            String format = super.getLog4jPattern();
            if (key != null) {
                format = format.replace(prefix, prefix + "{" + key + "}");
            }
            return format;
        }
    }

    /**
     * Represents a log format entry that formats and appends a marker value to a log output.
     * A marker is a string that can be used in log messages to provide additional context or categorization.
     */
    public static class MarkerEntry extends AbstractLogPatternEntry {
        /**
         * Constructs an instance of MarkerEntry with the specified formatting parameters.
         * A MarkerEntry formats and appends a marker value to the log output. A marker is
         * a string that provides additional context or categorization for log messages.
         *
         * @param minWidth  the minimum width of the formatted marker output. If the marker
         *                  output is shorter than this width, padding will be added to meet
         *                  the minimum length.
         * @param maxWidth  the maximum width of the formatted marker output. If the marker
         *                  output is longer than this width, it will be truncated to the
         *                  specified maximum length.
         * @param leftAlign a flag indicating whether the marker output should be left-aligned.
         *                  If true, padding will be added to the right of the marker output;
         *                  otherwise, padding will be added to the left.
         */
        public MarkerEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("marker", minWidth, maxWidth, leftAlign, false);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, mrk);
        }
    }

    /**
     * Represents a specific implementation of {@code AbstractLogPatternEntry} for formatting
     * log message content in a log entry. This class is responsible for formatting the
     * log message text according to the provided parameters for minimum width, maximum width,
     * and alignment.
     */
    public static class MessageEntry extends AbstractLogPatternEntry {
        /**
         * Constructs a new instance of MessageEntry with the specified formatting parameters.
         *
         * @param minWidth  the minimum width of the formatted output. If the output is shorter
         *                  than this width, padding will be added to meet the minimum length.
         * @param maxWidth  the maximum width of the formatted output. If the output is longer
         *                  than this width, it will be truncated to fit the specified maximum length.
         * @param leftAlign a flag indicating if the formatted output should be left-aligned. When set
         *                  to true, padding is added to the right of the output; otherwise, it is
         *                  added to the left.
         */
        public MessageEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("m", minWidth, maxWidth, leftAlign, false);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, msg.get());
        }
    }

    public static class ClassEntry extends AbstractLogPatternEntry {
        private final int abbreviationLength;

        /**
         * Constructs an instance of the ClassEntry, which represents a log entry
         * for the class name of the log event's location. This entry supports
         * formatting options such as minimum width, maximum width, and left alignment,
         * as well as an optional abbreviation length for the class name.
         *
         * @param minWidth         The minimum width of the formatted class name. If the
         *                         class name is shorter than this width, padding will
         *                         be added to meet this length.
         * @param maxWidth         The maximum width of the formatted class name. If the
         *                         class name exceeds this length, it will be truncated.
         * @param leftAlign        A flag indicating whether the formatted class name
         *                         should be left-aligned. If true, padding will be added
         *                         to the right; otherwise, it will be added to the left.
         * @param abbreviationLength The maximum number of dot-separated package name segments
         *                         to abbreviate in the class name. A value of 0 indicates
         *                         no abbreviation.
         */
        public ClassEntry(int minWidth, int maxWidth, boolean leftAlign, int abbreviationLength) {
            super("C", minWidth, maxWidth, leftAlign, true);
            this.abbreviationLength = abbreviationLength;
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            String className = location != null ? location.getClassName() : null;
            appendFormatted(app, className != null ? abbreviate(className, abbreviationLength) : null, true);
        }

        @Override
        public String getLog4jPattern() {
            String format = super.getLog4jPattern();
            if (abbreviationLength > 0) {
                format = format.replace(prefix, prefix + "{" + abbreviationLength + "}");
            }
            return format;
        }
    }

    /**
     * Represents a log pattern entry specifically designed to include the method name in a log message.
     * This class is a concrete implementation of {@link AbstractLogPatternEntry}, responsible for
     * formatting and appending the method name of the log's location information to the output.
     */
    public static class MethodEntry extends AbstractLogPatternEntry {
        /**
         * Constructs a {@code MethodEntry} instance for formatting log messages to include the method name of the log's location.
         *
         * @param minWidth The minimum width of the formatted output. If the method name is shorter than this width, padding will be added.
         * @param maxWidth The maximum width of the formatted output. If the method name exceeds this width, it will be truncated.
         * @param leftAlign A boolean indicating whether the formatted method name should be left-aligned. If {@code true}, padding is added to the right; otherwise, padding is added
         *  to the left.
         */
        public MethodEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("M", minWidth, maxWidth, leftAlign, true);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, location != null ? location.getMethodName() : null);
        }
    }

    /**
     * A specialized implementation of {@link AbstractLogPatternEntry} that formats
     * log entries by appending the line number from the logging location. If the
     * location is null, it appends a null value.
     */
    public static class LineEntry extends AbstractLogPatternEntry {
        /**
         * Constructs a LineEntry instance with specified formatting parameters for log entries.
         *
         * @param minWidth The minimum width of the formatted line entry. If the formatted
         *                 value is shorter than this width, padding will be added to meet
         *                 the minimum length.
         * @param maxWidth The maximum width of the formatted line entry. If the formatted
         *                 value is longer than this width, it will be truncated to meet
         *                 the specified maximum length.
         * @param leftAlign A flag indicating whether the formatted line entry should be
         *                  left-aligned. If true, padding will be added to the right side
         *                  of the formatted output; otherwise, it will be added to the left.
         */
        public LineEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("L", minWidth, maxWidth, leftAlign, true);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, location != null ? String.valueOf(location.getLineNumber()) : null);
        }
    }

    /**
     * A concrete implementation of {@link AbstractLogPatternEntry} that formats and appends
     * the name of the file associated with the location of the log event.
     */
    public static class FileEntry extends AbstractLogPatternEntry {
        /**
         * Constructs a FileEntry instance, which is a concrete implementation of
         * {@link AbstractLogPatternEntry} that formats and appends the file name
         * associated with the location of a log event.
         *
         * @param minWidth The minimum width of the formatted output. If the file name is shorter than
         *                 this width, padding will be added to meet the specified length.
         * @param maxWidth The maximum width of the formatted output. If the file name is longer than
         *                 this width, it will be truncated to fit the specified length.
         * @param leftAlign A flag indicating whether the formatted output should be left-aligned.
         *                  If true, padding will be added to the right; otherwise, padding
         *                  will be added to the left.
         */
        public FileEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("F", minWidth, maxWidth, leftAlign, true);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, location != null ? location.getFileName() : null);
        }
    }

    /**
     * Represents a log format entry for displaying location information within log messages.
     * <p>
     * This class formats the location string according to specified width and alignment constraints.
     */
    public static class LocationEntry extends AbstractLogPatternEntry {
        /**
         * Constructs an instance of the LocationEntry class. This constructor initializes
         * a log format entry responsible for formatting and displaying the location
         * information in log messages, with the specified formatting parameters.
         *
         * @param minWidth The minimum width of the formatted location output. If the location
         *                 string is shorter than this width, padding will be added to meet the
         *                 minimum length.
         * @param maxWidth The maximum width of the formatted location output. If the location
         *                 string is longer than this width, it will be truncated to the specified
         *                 maximum length.
         * @param leftAlign A flag indicating whether the formatted location output should be
         *                  left-aligned. If true, padding will be added to the right of the
         *                  formatted output; otherwise, padding will be added to the left.
         */
        public LocationEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("l", minWidth, maxWidth, leftAlign, true);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            if (location != null) {
                appendFormatted(app, location.toString());
            } else {
                appendFormatted(app, null);
            }
        }
    }

    /**
     * The ExceptionEntry class is a specialized implementation of the AbstractLogPatternEntry
     * for handling and formatting exception-related log entries. It formats the exception information
     * into the log output, including the exception type and message.
     */
    public static class ExceptionEntry extends AbstractLogPatternEntry {
        /**
         * Constructs an instance of ExceptionEntry, a specialized log format entry
         * that handles the formatting of exceptions in a logging framework. This
         * entry utilizes the specified parameters to format exception-related log output.
         *
         * @param minWidth the minimum width of the formatted output. If the output is
         *                 shorter than this width, padding will be applied to meet
         *                 the minimum length.
         * @param maxWidth the maximum width of the formatted output. If the output
         *                 exceeds this width, it will be truncated to conform to this
         *                 limit.
         * @param leftAlign a flag indicating whether the formatted output should be
         *                  left-aligned. If true, the padding will be added to the
         *                  right of the output; otherwise, padding will be applied
         *                  to the left.
         */
        public ExceptionEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("ex", minWidth, maxWidth, leftAlign, false);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            if (t != null) {
                Util.appendStackTrace(app, t);
            }
        }
    }

    /**
     * Represents a log format entry that injects the start of a color code into log formatting.
     * This is used to include terminal color codes in the log messages for enhanced visualization.
     * The color code to be inserted is specified via a pair of color codes passed as a parameter
     * during formatting.
     */
    public static class ColorStartEntry extends AbstractLogPatternEntry {
        /**
         * Constructs an instance of ColorStartEntry with the specified formatting parameters.
         *
         * @param minWidth the minimum width of the formatted output. If the formatted
         *                 log component is shorter than this width, padding will be
         *                 added to meet the minimum length.
         * @param maxWidth the maximum width of the formatted output. If the formatted
         *                 log component is longer than this width, it will be truncated
         *                 to the specified maximum length.
         * @param leftAlign a flag indicating whether the formatted output should be
         *                  left-aligned. If true, padding will be added to the right
         *                  of the formatted output; otherwise, padding will be added
         *                  to the left.
         */
        public ColorStartEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("Cstart", minWidth, maxWidth, leftAlign, false);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, consoleCodes == null ? "" : consoleCodes.start());
        }
    }

    /**
     * Represents a specific type of log format entry designed to insert an ending
     * color code into a log message.
     */
    public static class ColorEndEntry extends AbstractLogPatternEntry {
        /**
         * Constructs a ColorEndEntry instance used for formatting log entries with
         * specific width constraints and alignment settings.
         *
         * @param minWidth the minimum width of the formatted output. If the formatted
         *                 log component is shorter than this width, padding will be
         *                 added to meet the minimum length.
         * @param maxWidth the maximum width of the formatted output. If the formatted
         *                 log component is longer than this width, it will be truncated
         *                 to the specified maximum length.
         * @param leftAlign a flag indicating whether the formatted output should be
         *                  left-aligned. If true, padding will be added to the right
         *                  of the formatted output; otherwise, padding will be added
         *                  to the left.
         */
        public ColorEndEntry(int minWidth, int maxWidth, boolean leftAlign) {
            super("Cend", minWidth, maxWidth, leftAlign, false);
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, consoleCodes == null ? "" : consoleCodes.end());
        }
    }

    /**
     * A class that formats date-time values for log entries using a specified pattern.
     * This class implements the {@code LogPatternEntry} interface and provides functionality
     * to format a log entry's timestamp according to various date-time patterns.
     */
    public static class DateEntry implements LogPatternEntry {
        private final String datePattern;
        private final DateTimeFormatter formatter;

        /**
         * Constructs a {@code DateEntry} instance with the specified date-time pattern.
         *
         * @param pattern the pattern to be used for formatting date-time values;
         *                supported patterns include "ISO8601", "HH:mm:ss,SSS",
         *                "yyyy-MM-dd HH:mm:ss,SSS", "yyyy-MM-dd HH:mm:ss", or a custom pattern.
         *                If the pattern is empty, "HH:mm:ss" will be used as the default.
         */
        public DateEntry(String pattern) {
            this.datePattern = pattern;
            this.formatter = switch (pattern) {
                case "ISO8601" -> DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssSSSZ");
                case "HH:mm:ss,SSS" -> DateTimeFormatter.ofPattern("HH:mm:ss,SSS");
                case "yyyy-MM-dd HH:mm:ss,SSS" -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");
                case "yyyy-MM-dd HH:mm:ss" -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                default -> DateTimeFormatter.ofPattern(pattern.isEmpty() ? "HH:mm:ss" : pattern);
            };
        }

        @Override
        public String toString() {
            return getLog4jPattern();
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) {
            formatter.formatTo(instant.atZone(ZONE_ID), app);
        }

        @Override
        public String getLog4jPattern() {
            return datePattern.isEmpty() ? "%d" : "%d{" + datePattern + "}";
        }
    }

    /**
     * Represents a log format entry that inserts a newline character into the log output.
     */
    public static class NewlineEntry implements LogPatternEntry {
        @Override
        public String toString() {
            return getLog4jPattern();
        }

        @Override
        public void format(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
            app.append(NEWLINE);
        }

        @Override
        public String getLog4jPattern() {
            return "%n";
        }
    }

    @Override
    public String toString() {
        return getPattern();
    }

    private final List<LogPatternEntry> entries;

    /**
     * Parses a Log4J-style pattern string and creates a new {@code LogPattern} instance.
     *
     * @param pattern the format pattern in Log4J style, which may include placeholders and literals
     * @return a {@code LogPattern} instance representing the parsed pattern
     */
    public static LogPattern parse(String pattern) {
        return new LogPattern(pattern);
    }

    /**
     * Constructs a LogPattern using the supplied pattern.
     *
     * @param pattern the format pattern in Log4J style, which may include placeholders and literals
     */
    private LogPattern(String pattern) {
        this.entries = parseLog4jPatternString(pattern);
    }

    /**
     * Get the format pattern.
     * @return the format pattern in Log4J style
     */
    public String getPattern() {
        StringBuilder app = new StringBuilder();
        for (LogPatternEntry entry : entries) {
            app.append(entry.getLog4jPattern());
        }
        return app.toString();
    }

    /**
     * Checks if this pattern requires location information.
     *
     * @return {@code true} if this pattern requires location information, otherwise {@code false}
     */
    public boolean isLocationNeeded() {
        for (LogPatternEntry entry : entries) {
            if (entry.isLocationNeeded()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Formats a log entry.
     *
     * @param app          the {@link PrintStream} to write the formatted log entry to
     * @param instant      the timestamp of the log entry
     * @param loggerName   the name of the logger
     * @param lvl          the log level
     * @param mrk          the marker
     * @param mdc          the MDC context
     * @param loc          the location resolver
     * @param msg          the message supplier
     * @param t            the throwable, if any
     * @param consoleCodes the color codes for the log level (start and end)
     * @throws IOException if an I/O error occurs while writing to the appendable
     */
    public void formatLogEntry(Appendable app, Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, Supplier<@Nullable String> msg, @Nullable Throwable t, @Nullable ConsoleCode consoleCodes) throws IOException {
        Location location = isLocationNeeded() ? loc.resolve() : null;
        for (LogPatternEntry entry : entries) {
            entry.format(app, instant, loggerName, lvl, mrk, mdc, location, msg, t, consoleCodes);
        }
    }

    /**
     * Parses a Log4J-style pattern string and converts it into a list of {@code LogPatternEntry} instances,
     * which can be used to format log entries according to the specified pattern.
     * <p>
     * Supported pattern specifiers include literals, placeholders for log levels, messages, loggers, etc.,
     * as well as specific options for alignment and truncation.
     *
     * @param pattern the pattern string in Log4J style, which may include placeholders and literals
     * @return a list of {@code LogPatternEntry} instances representing the parsed pattern
     */
    private static List<LogPatternEntry> parseLog4jPatternString(String pattern) {
        List<LogPatternEntry> entries = new ArrayList<>();
        Matcher matcher = PATTERN.matcher(pattern);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                entries.add(new LiteralEntry(pattern.substring(lastEnd, matcher.start())));
            }

            String match = matcher.group();
            if (match.equals("%%")) {
                entries.add(new LiteralEntry("%"));
            } else if (match.equals("%n")) {
                entries.add(new NewlineEntry());
            } else {
                String minWidthStr = matcher.group(1);
                String maxWidthStr = matcher.group(2);
                String type = matcher.group(3);
                String options = matcher.group(5);

                boolean leftAlign = minWidthStr != null && minWidthStr.startsWith("-");
                int minWidth = (minWidthStr != null && !minWidthStr.isEmpty()) ? Math.abs(Integer.parseInt(minWidthStr)) : 0;
                int maxWidth = (maxWidthStr != null && maxWidthStr.length() > 1) ? Integer.parseInt(maxWidthStr.substring(1)) : 0;

                switch (type) {
                    case "p", "level" -> entries.add(new LevelEntry(minWidth, maxWidth, leftAlign));
                    case "c", "logger" -> {
                        int abbreviationLength = 0;
                        if (options != null && options.matches("\\d+")) {
                            abbreviationLength = Integer.parseInt(options);
                        }
                        entries.add(new LoggerEntry(minWidth, maxWidth, leftAlign, abbreviationLength));
                    }
                    case "C" -> {
                        int abbreviationLength = 0;
                        if (options != null && options.matches("\\d+")) {
                            abbreviationLength = Integer.parseInt(options);
                        }
                        entries.add(new ClassEntry(minWidth, maxWidth, leftAlign, abbreviationLength));
                    }
                    case "M" -> entries.add(new MethodEntry(minWidth, maxWidth, leftAlign));
                    case "L" -> entries.add(new LineEntry(minWidth, maxWidth, leftAlign));
                    case "F" -> entries.add(new FileEntry(minWidth, maxWidth, leftAlign));
                    case "marker" -> entries.add(new MarkerEntry(minWidth, maxWidth, leftAlign));
                    case "m", "msg", "message" -> entries.add(new MessageEntry(minWidth, maxWidth, leftAlign));
                    case "l", "location" -> entries.add(new LocationEntry(minWidth, maxWidth, leftAlign));
                    case "t", "thread" -> entries.add(new ThreadEntry(minWidth, maxWidth, leftAlign));
                    case "X", "mdc" -> entries.add(new MdcEntry(minWidth, maxWidth, leftAlign, options));
                    case "ex", "exception", "throwable" ->
                            entries.add(new ExceptionEntry(minWidth, maxWidth, leftAlign));
                    case "Cstart" -> entries.add(new ColorStartEntry(minWidth, maxWidth, leftAlign));
                    case "Cend" -> entries.add(new ColorEndEntry(minWidth, maxWidth, leftAlign));
                    case "d" -> entries.add(new DateEntry(options != null ? options : ""));
                    default -> entries.add(new LiteralEntry(match));
                }
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < pattern.length()) {
            entries.add(new LiteralEntry(pattern.substring(lastEnd)));
        }
        return entries;
    }
}
