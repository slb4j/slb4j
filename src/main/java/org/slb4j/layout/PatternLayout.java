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

import org.slb4j.ConsoleCode;
import org.slb4j.LogLayout;
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.support.formatter.PatternTimeStampFormatter;
import org.slb4j.support.TimeStampFormatter;
import org.slb4j.support.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The StandardLogPattern class handles the formatting of log entries using Log4J-style format strings.
 */
public final class PatternLayout implements LogLayout {

    private static final String NEWLINE = System.lineSeparator();

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    private static final Pattern PATTERN = Pattern.compile("%(-?\\d*)(\\.\\d+)?([a-zA-Z]+)(\\{([^}]+)?})?(\\{([^}]+)?})?(\\{([^}]+)?})?|%%|%(?![a-zA-Z])");

    private static final Pattern LOGGER_PRECISION_PATTERN = Pattern.compile("^(\\d+)(\\.)?$");

    /**
     * The default pattern string for log formatting.
     */
    public static final String DEFAULT_PATTERN_STRING = "%d{yyyy-MM-dd HH:mm:ss,SSS} [%t] %-5level %logger{36} - %msg%n";

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
    public static final LogLayout LAYOUT_INSTANCE_DEFAULT = parseLog4jPattern("%highlight{" + DEFAULT_PATTERN_STRING.replaceFirst("%n$", "%ex%n") +"}");

    /**
     * A compact log pattern used to format log entries in a concise and structured manner.
     * The pattern defines the format of log messages by specifying placeholders, alignment,
     * truncation, and other layout options.
     * <p>
     * Pattern description:
     * <ul>
     * <li>`%highlight{...}`: Highlights the enclosed pattern with console color codes (if isSupported by the logging system).
     * <li>`%d{HH:mm:ss.SSS}`: The timestamp of the log entry without the date.
     * <li>`%-5level`: The log level, left-aligned with a width of 5 characters.
     * <li>`%-30.30c{1.}`: The logger name, left-aligned and truncated to a maximum of 30 characters, showing only the first fragment of the name.
     * <li>`%msg`: The log message.
     * <li>`%n`: A new line character.
     * </ul>
     * Use when a compact and human-readable log format is preferred, such as console-based logging.
     */
    public static final LogLayout LAYOUT_INSTANCE_COMPACT = parseLog4jPattern("%highlight{%d{HH:mm:ss.SSS} %-5level %-30.30c{1.} - %msg}%ex%n");

    /**
     * A predefined {@link LogLayout} instance representing a detailed log format.
     * <p>
     * The format includes the following components:
     * <ul>
     * <li>Timestamp in the format yyyy-MM-dd HH:mm:ss.SSS
     * <li>Thread name enclosed in square brackets
     * <li>Log level with a minimum width of 5 characters
     * <li>Marker, if present
     * <li>Logger name truncated to a maximum of 36 characters
     * <li>Mapped Diagnostic Context (MDC) key-value pairs enclosed in square brackets
     * <li>Fully qualified class name, method name, file name, and line number
     * <li>Log message
     * <li>Throwable stack trace (if any)
     * </ul>
     * This format provides comprehensive information about log events, including contextual
     * details, useful for debugging and auditing purposes.
     */
    public static final LogLayout LAYOUT_INSTANCE_DETAILED = parseLog4jPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %marker %logger{36} [%X] (%class.%method(%file:%line)) - %msg%throwable%n");

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
         * @param timestamp the timestamp of the log event (milliseconds since epoch)
         * @param loggerName the name of the logger that generated the log event
         * @param lvl the {@link LogLevel} representing the severity level of the log event
         * @param mrk an optional marker for the log entry, or null if not provided
         * @param mdc an optional {@link MDC} containing diagnostic context data, or null if not provided
         * @param location an optional {@link Location} detailing the source of the log event, or null if unknown
         * @param msg a {@link Supplier} providing the log message, or null if not available
         * @param t an optional {@link Throwable} associated with the log event, or null if no exception occurred
         * @param consoleCodes a {@link ConsoleCode} defining console-specific format codes
         * @throws IOException if an I/O error occurs while appending to the {@link Appendable} instance
         */
        void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException;

        /**
         * Determines whether the location information (e.g., source file, line number)
         * is required for logging.
         *
         * @return a boolean value indicating whether location information is needed.
         */
        default boolean isLocationNeeded() {
            return false;
        }

        /**
         * Returns a header for the output format.
         *
         * @return the header, or an empty string if no header is required
         */
        default String getHeader() {
            return "";
        }

        /**
         * Returns a footer for the output format.
         *
         * @return the footer, or an empty string if no footer is required
         */
        default String getFooter() {
            return "";
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
        /**
         * The prefix string that identifies a specific log component.
         */
        protected final String prefix;

        private final int minWidth;
        private final int maxWidth;
        private final boolean leftAlign;
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

        private static final CharSequence SPACES = "                                      ";
        private static final int N_SPACES = SPACES.length();

        @Override
        public String toString() {
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
            switch (n) {
                case 0 -> { /* nothing to do */}
                case 1 -> app.append(' ');
                default -> {
                    while (n > 0) {
                        int count = Math.min(n, N_SPACES);
                        app.append(SPACES, 0, count);
                        n -= count;
                    }
                }
            }
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
         * @throws IOException if an I/O error occurs while appending to the {@link Appendable} instance
         */
        protected final void appendFormatted(Appendable app, @Nullable CharSequence value, boolean leftTruncate) throws IOException {
            if (value == null || value.isEmpty()) {
                appendSpaces(app, minWidth);
                return;
            }

            // Truncates string if it exceeds maximum width
            int length = value.length();
            if (maxWidth > 0 && length > maxWidth) {
                if (leftTruncate) {
                    app.append(value, length - maxWidth, length);
                } else {
                    app.append(value, 0, maxWidth);
                }
                return;
            }

            int padding = Math.max(0, minWidth - length);
            if (leftAlign) {
                app.append(value);
                appendSpaces(app, padding);
            } else {
                appendSpaces(app, padding);
                app.append(value);
            }
        }
    }

    /**
     * An optimized pattern entry for default log format:
     * <pre>
     * %d{yyyy-MM-dd HH:mm:ss,SSS} [%t] %-5level %logger{36} - %msg%n
     * </pre>
     */
    public static final class DefaultPatternEntry implements LogPatternEntry {
        private TimeStampFormatter timeStampFormatter = PatternTimeStampFormatter.parse("yyyy-MM-dd HH:mm:ss,SSS", ZONE_ID, Locale.getDefault());
        private LevelEntry levelEntry = new LevelEntry(5, 5, true);
        private LoggerEntry loggerEntry = new LoggerEntry(0, 0, false, 36, false);

        /**
         * Default constructor.
         */
        public DefaultPatternEntry() { /* nothing to do */}

        @Override
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            timeStampFormatter.appendTo(timestamp, app);
            app.append(" [");
            app.append(Thread.currentThread().getName());
            app.append("] ");
            levelEntry.format(app, timestamp, loggerName, lvl, mrk, mdc, location, msg, t, consoleCodes);
            app.append(' ');
            loggerEntry.format(app, timestamp, loggerName, lvl, mrk, mdc, location, msg, t, consoleCodes);
            app.append(" - ");
            app.append(msg);
            app.append(NEWLINE);
        }
    }

    /**
     * Represents a literal string entry in a log format.
     */
    public static final class LiteralEntry implements LogPatternEntry {
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
            return literal.replace("%", "%%");
        }

        @Override
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            app.append(literal);
        }
    }

    /**
     * Represents a specific log format entry for handling log levels within log messages.
     * <p>
     * Instances of this class are responsible for converting a {@code LogLevel} to a string
     * and applying formatting options such as alignment and truncation based on the
     * parent class's configuration.
     */
    public static final class LevelEntry extends AbstractLogPatternEntry {
        private final boolean useJulNames;

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
         * @param useJulNames A flag indicating whether to use JUL level names (e.g., INFORMATION).
         */
        public LevelEntry(int minWidth, int maxWidth, boolean leftAlign, boolean useJulNames) {
            super("p", minWidth, maxWidth, leftAlign, false);
            this.useJulNames = useJulNames;
        }

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
            this(minWidth, maxWidth, leftAlign, false);
        }

        @Override
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            String name = useJulNames ? translateToJulLevelName(lvl) : lvl.name();
            appendFormatted(app, name, false);
        }

        private static String translateToJulLevelName(LogLevel lvl) {
            return switch (lvl) {
                case TRACE -> "FINER";
                case DEBUG -> "FINE";
                case INFO -> "INFO";
                case WARN -> "WARNING";
                case ERROR -> "SEVERE";
            };
        }
    }

    private static CharSequence abbreviate(String name, int abbreviationLength, boolean useDotAbbreviation) {
        if (abbreviationLength <= 0 && !useDotAbbreviation) {
            return name;
        }
        String[] parts = name.split("\\.");
        if (useDotAbbreviation) {
            return joinAbbreviations(abbreviationLength, parts);
        }
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
        return abbreviated;
    }

    private static CharSequence joinAbbreviations(int abbreviationLength, String[] parts) {
        int length = abbreviationLength > 0 ? abbreviationLength : 1;
        StringBuilder abbreviated = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.length() > length) {
                abbreviated.append(part, 0, length);
            } else {
                abbreviated.append(part);
            }
            abbreviated.append('.');
        }
        abbreviated.append(parts[parts.length - 1]);
        return abbreviated;
    }

    /**
     * A specialized log format entry for formatting and appending logger names to log messages.
     * <p>
     * Instances of this class are responsible for appending the logger name to a {@code Appendable}
     * with formatting applied according to the specified minimum width, maximum width, and alignment
     * settings. If truncation is required, the logger name will be truncated from the left.
     */
    public static final class LoggerEntry extends AbstractLogPatternEntry {
        private final int abbreviationLength;
        private final boolean useDotAbbreviation;
        private final Map<String, String> loggerNames = new ConcurrentHashMap<>(128);

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
         * @param useDotAbbreviation a flag indicating whether to use dot abbreviation (e.g., "o.s.T").
         */
        public LoggerEntry(int minWidth, int maxWidth, boolean leftAlign, int abbreviationLength, boolean useDotAbbreviation) {
            super("c", minWidth, maxWidth, leftAlign, false);
            this.abbreviationLength = abbreviationLength;
            this.useDotAbbreviation = useDotAbbreviation;
        }

        @Override
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            String logger = loggerNames.get(loggerName); // lambda in computeIfAbsent causes memory allocation!
            if (logger == null) {
                logger = abbreviate(loggerName, abbreviationLength, useDotAbbreviation).toString();
                loggerNames.put(loggerName, logger);
            }
            appendFormatted(app, logger, true);
        }

        @Override
        public String toString() {
            String format = super.toString();
            if (abbreviationLength > 0 || useDotAbbreviation) {
                format = format.replace(prefix, prefix + "{" + abbreviationLength + (useDotAbbreviation ? "." : "") + "}");
            }
            return format;
        }
    }

    /**
     * Represents a log format entry for the thread name.
     */
    public static final class ThreadEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, Thread.currentThread().getName(), false);
        }
    }

    /**
     * Represents a log format entry for the MDC (Mapped Diagnostic Context).
     */
    public static final class MdcEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            if (mdc == null) {
                return;
            }
            if (key != null) {
                appendFormatted(app, mdc.get(key), false);
            } else {
                app.append('{');
                boolean first = true;
                for (var e : mdc.get().entrySet()) {
                    if (!first) app.append(", ");
                    first = false;
                    app.append(e.getKey()).append('=').append(e.getValue());
                }
                app.append('}');
            }
        }

        @Override
        public String toString() {
            String format = super.toString();
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
    public static final class MarkerEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, mrk, false);
        }
    }

    /**
     * Represents a specific implementation of {@code AbstractLogPatternEntry} for formatting
     * log message content in a log entry. This class is responsible for formatting the
     * log message text according to the provided parameters for minimum width, maximum width,
     * and alignment.
     */
    public static final class MessageEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, msg, false);
        }
    }

    /**
     * Represents a log entry for the class name of the log event's location.
     */
    public static final class ClassEntry extends AbstractLogPatternEntry {
        private final int abbreviationLength;
        private final boolean useDotAbbreviation;
        private final Map<String, String> classNames = new ConcurrentHashMap<>(128);

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
         * @param useDotAbbreviation a flag indicating whether to use dot abbreviation (e.g., "o.s.T").
         */
        public ClassEntry(int minWidth, int maxWidth, boolean leftAlign, int abbreviationLength, boolean useDotAbbreviation) {
            super("C", minWidth, maxWidth, leftAlign, true);
            this.abbreviationLength = abbreviationLength;
            this.useDotAbbreviation = useDotAbbreviation;
        }

        @Override
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            String locationClassName = location == null ? null : location.getClassName();

            if (locationClassName == null) {
                return;
            }

            String className = classNames.get(locationClassName);
            if (className == null) {
                className = abbreviate(locationClassName, abbreviationLength, useDotAbbreviation).toString();
                classNames.put(locationClassName, className);
            }

            appendFormatted(app, className, true);
        }

        @Override
        public String toString() {
            String format = super.toString();
            if (abbreviationLength > 0 || useDotAbbreviation) {
                format = format.replace(prefix, prefix + "{" + abbreviationLength + (useDotAbbreviation ? "." : "") + "}");
            }
            return format;
        }
    }

    /**
     * Represents a log pattern entry specifically designed to include the method name in a log message.
     * This class is a concrete implementation of {@link AbstractLogPatternEntry}, responsible for
     * formatting and appending the method name of the log's location information to the output.
     */
    public static final class MethodEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, location != null ? location.getMethodName() : null, false);
        }
    }

    /**
     * A specialized implementation of {@link AbstractLogPatternEntry} that formats
     * log entries by appending the line number from the logging location. If the
     * location is null, it appends a null value.
     */
    public static final class LineEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, location != null ? Integer.toString(location.getLineNumber()) : null, false);
        }
    }

    /**
     * A concrete implementation of {@link AbstractLogPatternEntry} that formats and appends
     * the name of the file associated with the location of the log event.
     */
    public static final class FileEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            appendFormatted(app, location != null ? location.getFileName() : null, false);
        }
    }

    /**
     * Represents a log format entry for displaying location information within log messages.
     * <p>
     * This class formats the location string according to specified width and alignment constraints.
     */
    public static final class LocationEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            if (location != null) {
                StringBuilder sb = new StringBuilder();
                String className = location.getClassName();
                if (className != null) {
                    sb.append(className);
                }
                String methodName = location.getMethodName();
                if (methodName != null) {
                    sb.append('.').append(methodName);
                }
                sb.append('(');
                String fileName = location.getFileName();
                if (fileName != null) {
                    sb.append(fileName);
                }
                int lineNumber = location.getLineNumber();
                if (lineNumber >= 0) {
                    sb.append(':').append(lineNumber);
                }
                sb.append(')');
                appendFormatted(app, sb.toString(), false);
            } else {
                appendFormatted(app, null, false);
            }
        }
    }

    /**
     * The ExceptionEntry class is a specialized implementation of the AbstractLogPatternEntry
     * for handling and formatting exception-related log entries. It formats the exception information
     * into the log output, including the exception type and message.
     */
    public static final class ExceptionEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            if (t != null) {
                Util.appendStackTrace(app, t);
                app.append(NEWLINE);
            }
        }
    }

    /**
     * Represents a log format entry that injects the start of a color code into log formatting.
     * This is used to include terminal color codes in the log messages for enhanced visualization.
     * The color code to be inserted is specified via a pair of color codes passed as a parameter
     * during formatting.
     */
    public static final class ColorStartEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            app.append(consoleCodes.start());
        }
    }

    /**
     * Represents a specific type of log format entry designed to insert an ending
     * color code into a log message.
     */
    public static final class ColorEndEntry extends AbstractLogPatternEntry {
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
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            app.append(consoleCodes.end());
        }
    }

    /**
     * A class that formats date-time values for log entries using a specified pattern.
     * This class implements the {@code LogPatternEntry} interface and provides functionality
     * to format a log entry's timestamp according to various date-time patterns.
     */
    public static final class DateEntry implements LogPatternEntry {
        private final String datePattern;
        private final Locale locale;
        private final TimeStampFormatter formatter;

        /**
         * Constructs a {@code DateEntry} instance with the specified date-time pattern.
         *
         * @param pattern the pattern to be used for formatting date-time values;
         *                isSupported patterns include "ISO8601", "HH:mm:ss,SSS",
         *                "yyyy-MM-dd HH:mm:ss,SSS", "yyyy-MM-dd HH:mm:ss", or a custom pattern.
         *                If the pattern is empty, "HH:mm:ss" will be used as the default.
         */
        public DateEntry(String pattern) {
            this(pattern, Locale.getDefault());
        }

        /**
         * Constructs a {@code DateEntry} instance with the specified date-time pattern and locale.
         *
         * @param pattern the pattern to be used for formatting date-time values.
         * @param locale  the locale to be used for formatting.
         */
        public DateEntry(String pattern, Locale locale) {
            this.datePattern = pattern;
            this.locale = locale;
            this.formatter = (switch (pattern) {
                case "ISO8601" -> PatternTimeStampFormatter.parse("yyyy-MM-dd'T'HH:mm:ss,SSS", ZONE_ID, locale);
                case "HH:mm:ss,SSS" -> PatternTimeStampFormatter.parse("HH:mm:ss,SSS", ZONE_ID, locale);
                case "yyyy-MM-dd HH:mm:ss,SSS" -> PatternTimeStampFormatter.parse("yyyy-MM-dd HH:mm:ss,SSS", ZONE_ID, locale);
                case "yyyy-MM-dd HH:mm:ss" -> PatternTimeStampFormatter.parse("yyyy-MM-dd HH:mm:ss", ZONE_ID, locale);
                default -> PatternTimeStampFormatter.parse(pattern.isEmpty() ? "HH:mm:ss" : pattern, ZONE_ID, locale);
            });
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("%d");
            if (!datePattern.isEmpty()) {
                sb.append("{").append(datePattern).append("}");
            }
            if (!locale.equals(Locale.getDefault())) {
                sb.append("{").append(locale.toLanguageTag()).append("}");
            }
            return sb.toString();
        }

        @Override
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            formatter.appendTo(timestamp, app);
        }
    }

    /**
     * Represents a log format entry that inserts a newline character into the log output.
     */
    public static final class NewlineEntry implements LogPatternEntry {

        /**
         * Constructs a new instance of the NewlineEntry class which represents a log format entry
         * that inserts a newline character into the output of a log pattern.
         */
        public NewlineEntry() {
            // nothing to  do
        }

        @Override
        public String toString() {
            return "%n";
        }

        @Override
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            app.append(NEWLINE);
        }
    }

    /**
     * Represents a log format entry in CSV output.
     */
    public static final class CsvEntry implements LogPatternEntry {
        TimeStampFormatter formatter = PatternTimeStampFormatter.parse("yyyy-MM-dd HH:mm:ss,SSS", ZONE_ID, Locale.getDefault());

        /**
         * Constructs a new instance of the NewlineEntry class which represents a log format entry
         * that inserts a newline character into the output of a log pattern.
         */
        public CsvEntry() {
            // nothing to  do
        }

        @Override
        public String toString() {
            return "CSV";
        }

        @Override
        public void format(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location location, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
            app.append('"');
            formatter.appendTo(timestamp, app);
            app.append("\",\"");
            app.append(lvl.name());
            app.append("\",\"");
            app.append(loggerName);
            app.append("\",\"");
            appendCsvEscaped(app, msg);
            app.append("\"\n");
        }

        private static void appendCsvEscaped(Appendable app, @Nullable String msg) throws IOException {
            if (msg == null) {
                app.append("null");
                return;
            }

            int start = 0;
            int end;
            while ((end = msg.indexOf('"', start)) != -1) {
                app.append(msg, start, end);
                app.append("\"\"");
                start = end + 1;
            }
            app.append(msg, start, msg.length());
        }
    }

    /**
     * Parses a JUL-style pattern string and creates a new {@code LogPattern} instance.
     *
     * @param pattern the format pattern in JUL style
     * @return a {@code LogPattern} instance representing the parsed pattern
     */
    public static LogLayout parseJulPattern(String pattern) {
        return new PatternLayout(pattern, parseJulPatternString(pattern));
    }

    private static LogPatternEntry[] parseJulPatternString(String pattern) {
        List<LogPatternEntry> entries = new ArrayList<>();
        int len = pattern.length();
        int i = 0;
        while (i < len) {
            char c = pattern.charAt(i);
            if (c == '%') {
                if (i + 1 < len && pattern.charAt(i + 1) == 'n') {
                    entries.add(new NewlineEntry());
                    i += 2;
                    continue;
                }
                if (i + 1 < len && pattern.charAt(i + 1) == '%') {
                    entries.add(new LiteralEntry("%"));
                    i += 2;
                    continue;
                }

                int j = i + 1;
                while (j < len && (Character.isDigit(pattern.charAt(j)) || pattern.charAt(j) == '$')) {
                    j++;
                }

                if (j < len) {
                    String fullPlaceholder = pattern.substring(i, Math.min(j + 2, len));
                    if (fullPlaceholder.startsWith("%1$t")) {
                        if (j + 1 < len) {
                            char sub = pattern.charAt(j + 1);
                            entries.add(switch (sub) {
                                case 'Y' -> new DateEntry("yyyy");
                                case 'm' -> new DateEntry("MM");
                                case 'd' -> new DateEntry("dd");
                                case 'H' -> new DateEntry("HH");
                                case 'M' -> new DateEntry("mm");
                                case 'S' -> new DateEntry("ss");
                                case 'L' -> new DateEntry("SSS");
                                case 'b' -> new DateEntry("MMM");
                                case 'l' -> new DateEntry("h");
                                case 'p', 'P', 'T' -> new DateEntry("a");
                                case 'B' -> new DateEntry("MMMM");
                                case 'A' -> new DateEntry("EEEE");
                                case 'a' -> new DateEntry("EEE");
                                default -> new LiteralEntry(pattern.substring(i, j + 2));
                            });
                            i = j + 2;
                            continue;
                        }
                    } else if (fullPlaceholder.startsWith("%1$T")) {
                        if (j + 1 < len) {
                            char sub = pattern.charAt(j + 1);
                            entries.add(switch (sub) {
                                case 'p', 'P' -> new DateEntry("a");
                                default -> new LiteralEntry(pattern.substring(i, j + 2));
                            });
                            i = j + 2;
                            continue;
                        }
                    } else if (fullPlaceholder.startsWith("%2$s")) {
                        entries.add(new ClassEntry(0, 0, false, 0, false));
                        entries.add(new LiteralEntry(" "));
                        entries.add(new MethodEntry(0, 0, false));
                        i = j + 1;
                        continue;
                    } else if (fullPlaceholder.startsWith("%3$s")) {
                        entries.add(new LoggerEntry(0, 0, false, 0, false));
                        i = j + 1;
                        continue;
                    } else if (fullPlaceholder.startsWith("%4$s")) {
                        entries.add(new LevelEntry(0, 0, false, true));
                        i = j + 1;
                        continue;
                    } else if (fullPlaceholder.startsWith("%5$s")) {
                        entries.add(new MessageEntry(0, 0, false));
                        i = j + 1;
                        continue;
                    } else if (fullPlaceholder.startsWith("%6$s")) {
                        entries.add(new ExceptionEntry(0, 0, false));
                        i = j + 1;
                        continue;
                    }
                }
                entries.add(new LiteralEntry("%"));
                i++;
            } else {
                int nextPercent = pattern.indexOf('%', i);
                if (nextPercent == -1) {
                    entries.add(new LiteralEntry(pattern.substring(i)));
                    break;
                } else {
                    entries.add(new LiteralEntry(pattern.substring(i, nextPercent)));
                    i = nextPercent;
                }
            }
        }
        return entries.toArray(LogPatternEntry[]::new);
    }

    @Override
    public String toString() {
        return getType() + "[" + getText() + "]";
    }

    /**
     * Parses a Log4J-style pattern string and creates a new {@code LogPattern} instance.
     *
     * @param pattern the format pattern in Log4J style, which may include placeholders and literals
     * @return a {@code LogPattern} instance representing the parsed pattern
     */
    public static LogLayout parseLog4jPattern(String pattern) {
        return new PatternLayout(pattern, parseLog4jPatternString(pattern));
    }

    private final String text;
    private final LogPatternEntry[] entries;
    private final boolean locationNeeded;

    /**
     * Constructs a LogPattern using the supplied entries.
     *
     * @param pattern    the pattern text
     * @param entries the format pattern entries
     */
    public PatternLayout(String pattern, LogPatternEntry... entries) {
        this.text = pattern;
        this.entries = entries;

        boolean locationNeeded = false;
        for (LogPatternEntry entry : entries) {
            if (entry.isLocationNeeded()) {
                locationNeeded = true;
                break;
            }
        }
        this.locationNeeded = locationNeeded;
    }

    /**
     * Get the type of the pattern.
     * @return the type
     */
    public String getType() {
        return "PatternLayout";
    }

    /**
     * Get the pattern text.
     * @return the pattern text
     */
    @Override
    public String getText() {
        return text;
    }

    /**
     * Checks if this pattern requires location information.
     *
     * @return {@code true} if this pattern requires location information, otherwise {@code false}
     */
    @Override
    public boolean isLocationNeeded() {
        return locationNeeded;
    }

    /**
     * Get the header for this pattern.
     *
     * @return the header
     */
    @Override
    public String getHeader() {
        StringBuilder sb = new StringBuilder(entries.length * 16);
        for (int i = 0; i < entries.length; i++) {
            sb.append(entries[i].getHeader());
        }
        return sb.toString();
    }

    /**
     * Get the footer for this pattern.
     *
     * @return the footer
     */
    @Override
    public String getFooter() {
        StringBuilder sb = new StringBuilder();
        for (int i = entries.length - 1; i >= 0; i--) {
            sb.append(entries[i].getFooter());
        }
        return sb.toString();
    }

    /**
     * Formats a log entry.
     *
     * @param app          the {@link java.io.PrintStream} to write the formatted log entry to
     * @param timestamp    the timestamp of the log entry (milliseconds since epoch)
     * @param loggerName   the name of the logger
     * @param lvl          the log level
     * @param mrk          the marker
     * @param mdc          the MDC context
     * @param loc          the location resolver
     * @param msg          the message
     * @param t            the throwable, if any
     * @param consoleCodes the color codes for the log level (start and end)
     * @throws IOException if an I/O error occurs while writing to the appendable
     */
    public void formatLogEntry(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
        for (int i = 0; i < entries.length; i++) {
            entries[i].format(app, timestamp, loggerName, lvl, mrk, mdc, loc, msg, t, consoleCodes);
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
     * @return an array of {@code LogPatternEntry} instances representing the parsed pattern
     */
    public static LogPatternEntry[] parseLog4jPatternString(String pattern) {
        List<LogPatternEntry> entries = new ArrayList<>();
        int lastEnd = 0;
        int highlightStart;
        while ((highlightStart = pattern.indexOf("%highlight{", lastEnd)) != -1) {
            if (highlightStart > lastEnd) {
                entries.addAll(parseLog4jPatternStringSimple(pattern.substring(lastEnd, highlightStart)));
            }

            int contentStart = highlightStart + "%highlight{".length();
            int highlightEnd = findMatchingBrace(pattern, contentStart);

            if (highlightEnd != -1) {
                entries.add(new ColorStartEntry(0, 0, false));
                entries.addAll(parseLog4jPatternStringSimple(pattern.substring(contentStart, highlightEnd)));
                entries.add(new ColorEndEntry(0, 0, false));
                lastEnd = highlightEnd + 1;
            } else {
                // Fallback if no matching brace is found
                entries.addAll(parseLog4jPatternStringSimple(pattern.substring(highlightStart, highlightStart + "%highlight".length())));
                lastEnd = highlightStart + "%highlight".length();
            }
        }
        if (lastEnd < pattern.length()) {
            entries.addAll(parseLog4jPatternStringSimple(pattern.substring(lastEnd)));
        }

        return entries.toArray(LogPatternEntry[]::new);
    }

    private static int findMatchingBrace(String pattern, int start) {
        int depth = 1;
        for (int i = start; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private record AbbreviationSettings(int abbreviationLength, boolean useDotAbbreviation) {
        public static final AbbreviationSettings DEFAULT = new AbbreviationSettings(0, false);

        public static AbbreviationSettings forOptions(@Nullable String options) {
            int abbreviationLength = 0;
            boolean useDotAbbreviation = false;
            if (options != null) {
                Matcher m = LOGGER_PRECISION_PATTERN.matcher(options);
                if (m.matches()) {
                    abbreviationLength = Integer.parseInt(m.group(1));
                    useDotAbbreviation = m.group(2) != null;
                }
            }
            return new AbbreviationSettings(abbreviationLength, useDotAbbreviation);
        }
    }

    private static List<LogPatternEntry> parseLog4jPatternStringSimple(String pattern) {
        List<LogPatternEntry> entries = new ArrayList<>();

        if (pattern.startsWith(DEFAULT_PATTERN_STRING)) {
            entries.add(new DefaultPatternEntry());
            pattern = pattern.substring(DEFAULT_PATTERN_STRING.length());
        }

        Matcher matcher = PATTERN.matcher(pattern);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String literal = pattern.substring(lastEnd, matcher.start());
                entries.addAll(parseLiterals(literal));
            }

            String match = matcher.group();
            String minWidthStr = matcher.group(1);
            String maxWidthStr = matcher.group(2);
            String type = matcher.group(3);
            String options = matcher.group(5);
            String secondBlock = matcher.group(7);
            String thirdBlock = matcher.group(9);

            boolean leftAlign = minWidthStr != null && minWidthStr.startsWith("-");
            int minWidth = (minWidthStr != null && !minWidthStr.isEmpty()) ? Math.abs(Integer.parseInt(minWidthStr)) : 0;
            int maxWidth = (maxWidthStr != null && maxWidthStr.length() > 1) ? Integer.parseInt(maxWidthStr.substring(1)) : 0;

            switch (type != null ? type : match) {
                case "p", "level" -> entries.add(new LevelEntry(minWidth, maxWidth, leftAlign, false));
                case "c", "logger" -> {
                    AbbreviationSettings abbr = AbbreviationSettings.forOptions(options);
                    entries.add(new LoggerEntry(minWidth, maxWidth, leftAlign, abbr.abbreviationLength, abbr.useDotAbbreviation));
                }
                case "C", "class" -> {
                    AbbreviationSettings abbr = AbbreviationSettings.forOptions(options);
                    entries.add(new ClassEntry(minWidth, maxWidth, leftAlign, abbr.abbreviationLength, abbr.useDotAbbreviation));
                }
                case "M", "method" -> entries.add(new MethodEntry(minWidth, maxWidth, leftAlign));
                case "L", "line" -> entries.add(new LineEntry(minWidth, maxWidth, leftAlign));
                case "F", "file" -> entries.add(new FileEntry(minWidth, maxWidth, leftAlign));
                case "marker" -> entries.add(new MarkerEntry(minWidth, maxWidth, leftAlign));
                case "m", "msg", "message" -> entries.add(new MessageEntry(minWidth, maxWidth, leftAlign));
                case "l", "location" -> entries.add(new LocationEntry(minWidth, maxWidth, leftAlign));
                case "t", "thread" -> entries.add(new ThreadEntry(minWidth, maxWidth, leftAlign));
                case "X", "mdc" -> entries.add(new MdcEntry(minWidth, maxWidth, leftAlign, options));
                case "ex", "exception", "throwable" -> entries.add(new ExceptionEntry(minWidth, maxWidth, leftAlign));
                case "Cstart" -> entries.add(new ColorStartEntry(minWidth, maxWidth, leftAlign));
                case "Cend" -> entries.add(new ColorEndEntry(minWidth, maxWidth, leftAlign));
                case "d" -> {
                    Locale locale = Locale.getDefault();
                    String localeStr = thirdBlock;
                    if (localeStr == null && secondBlock != null && !isTimeZone(secondBlock)) {
                        localeStr = secondBlock;
                    }
                    if (localeStr != null) {
                        locale = Locale.forLanguageTag(localeStr.replace('_', '-'));
                    }
                    entries.add(new DateEntry(options != null ? options : "", locale));
                }
                case "%%", "%" -> entries.add(new LiteralEntry("%"));
                case "%n", "n" -> entries.add(new NewlineEntry());
                default -> entries.add(new LiteralEntry(match));
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < pattern.length()) {
            String literal = pattern.substring(lastEnd);
            entries.add(new LiteralEntry(literal));
        }
        return entries;
    }

    private static boolean isTimeZone(String s) {
        if (s.isEmpty()) return false;
        if (s.equals("Z") || s.startsWith("UTC") || s.startsWith("GMT") || s.startsWith("UT")) return true;
        if (s.contains("/") || s.contains("+") || s.contains("-")) return true;
        return ZoneId.getAvailableZoneIds().contains(s);
    }

    private static List<LogPatternEntry> parseLiterals(String literal) {
        List<LogPatternEntry> entries = new ArrayList<>();
        entries.add(new LiteralEntry(literal));
        return entries;
    }
}
