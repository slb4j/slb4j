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

import org.slb4j.filter.CombinedFilter;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The LogEntryFilter interface represents a filter used to determine if a LogEntry should be included or excluded.
 *
 * <p>The LogEntryFilter interface is a functional interface and can therefore be used as the assignment target for a lambda expression or method reference.
 */
public interface LogFilter {

    /**
     * Returns a LogEntryFilter that allows all log entries to pass through.
     *
     * @return a LogEntryFilter that allows all log entries to pass through
     */
    static LogFilter allPass() {
        return LogFilterConstants.ALL_PASS_FILTER;
    }

    /**
     * Returns a LogEntryFilter that allows no log entries to pass through.
     *
     * @return a LogEntryFilter that allows no log entries to pass through
     */
    static LogFilter nonePass() {return LogFilterConstants.NONE_PASS_FILTER;}

    /**
     * Combines multiple {@code LogFilter} instances into a single filter.
     *
     * @param filters the array of {@code LogFilter} instances to be combined; can be empty or null
     * @return a new {@code LogFilter} instance that represents the combined behavior of the provided filters
     */
    static LogFilter combine(LogFilter... filters) {
        LogFilter filter = allPass();
        for (LogFilter next : filters) {
            filter = filter.andThen(next);
        }
        return filter;
    }

    /**
     * Returns a new {@code LogFilter} that applies the provided filter first,
     * and then applies this filter to the result.
     *
     * @param filter the {@code LogFilter} to be applied before this filter; must not be null
     * @return a {@code LogFilter} representing the composition of the provided filter and this filter
     */
    default LogFilter firstApply(LogFilter filter) {
        return filter.andThen(this);
    }

    /**
     * Returns a new {@code LogFilter} that represents the composition of this filter and another filter.
     * The resulting filter applies the current filter first, and then applies the specified filter to the
     * result. If the specified filter is {@code LogFilterConstants.ALL_PASS_FILTER}, this filter is returned
     * as is. If the specified filter is {@code LogFilterConstants.NONE_PASS_FILTER}, the specified filter
     * is returned as is.
     *
     * @param other the {@code LogFilter} to be applied after this filter; must not be null
     * @return a {@code LogFilter} representing the composition of this filter and the specified filter
     */
    default LogFilter andThen(LogFilter other) {
        if (other.equals(LogFilterConstants.ALL_PASS_FILTER)) return this;
        if (other.equals(LogFilterConstants.NONE_PASS_FILTER)) return other;
        if (other instanceof CombinedFilter cf) return cf.firstApply(this);

        return new CombinedFilter(this, other);
    }

    /**
     * Retrieves the name associated with this filter.
     *
     * @return the name of the filter as a String
     */
    String name();

    /**
     * Tests if a log entry, specified by its components, should be processed.
     *
     * @param timestamp  the timestamp of the log entry (milliseconds since epoch)
     * @param loggerName the name of the logger that generated the log entry
     * @param lvl        the log level of the log entry
     * @param mrk        the marker associated with the log entry, can be {@code null}
     * @param mdc        the MDC associated with the log entry, can be {@code null}
     * @param msg        the message of the log entry
     * @param t          the throwable associated with the log entry, can be {@code null}
     * @return {@code true}, if the log entry should be processed, {@code false} if it should be filtered out
     */
    boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, Supplier<@Nullable String> msg, @Nullable Throwable t);

    /**
     * Determines if logging is enabled for a specific name, log level, and optional marker.
     *
     * @param loggerName the name to check for logging enablement
     * @param logLevel the log level to evaluate
     * @param marker the marker associated with the log entry, can be {@code null}
     * @return {@code true} if logging is enabled for the provided parameters, {@code false} otherwise
     */
    default boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
        return isLevelEnabled(logLevel) && isMarkerEnabled(marker);
    }

    /**
     * Determines whether logging is enabled for the specified log level.
     *
     * @param logLevel the log level to check for enablement
     * @return {@code true} if logging is enabled for the given log level, {@code false} otherwise
     */
    default boolean isLevelEnabled(LogLevel logLevel) {
        return true;
    }

    /**
     * Determines whether logging is enabled for a specific marker.
     *
     * @param marker the marker associated with the log entry, can be {@code null}
     * @return {@code true} if logging is enabled for the provided marker, {@code false} otherwise
     */
    default boolean isMarkerEnabled(@Nullable String marker) {
        return true;
    }
}

final class LogFilterConstants {
    private LogFilterConstants() {
    }

    /**
     * A LogFilter that allows all log entries to pass through.
     *
     * <p>This filter implementation always returns true, indicating that all log entries should be included and
     * none filtered out.
     */
    static final LogFilter ALL_PASS_FILTER = new LogFilter() {
        @Override
        public String name() {
            return "ALL PASS";
        }

        @Override
        public boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, Supplier<@Nullable String> msg, @Nullable Throwable t) {
            return true;
        }

        @Override
        public boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
            return true;
        }

        @Override
        public LogFilter andThen(LogFilter other) {
            return other;
        }
    };

    /**
     * A LogFilter that allows no log entries to pass through.
     *
     * <p>This filter implementation always returns false, indicating that all log entries should be filtered out.
     */
    static final LogFilter NONE_PASS_FILTER = new LogFilter() {
        @Override
        public String name() {
            return "NONE PASS";
        }

        @Override
        public boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, Supplier<@Nullable String> msg, @Nullable Throwable t) {
            return false;
        }

        @Override
        public boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
            return false;
        }

        @Override
        public boolean isLevelEnabled(LogLevel logLevel) {
            return false;
        }

        @Override
        public boolean isMarkerEnabled(@Nullable String marker) {
            return false;
        }

        @Override
        public LogFilter andThen(LogFilter other) {
            return this;
        }
    };
}