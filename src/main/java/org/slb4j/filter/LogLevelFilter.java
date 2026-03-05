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
package org.slb4j.filter;

import org.slb4j.LogFilter;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * The DefaultLogEntryFilter class is an implementation of the LogEntryFilter interface
 * that filters log entries based on their log level and a user-defined filter.
 *
 * <p>DefaultLogEntryFilter provides methods to set and retrieve the log level and filter,
 * as well as a test method to determine if a LogEntry should be included or excluded.
 */
public final class LogLevelFilter implements LogFilter {

    private final String name;
    private final boolean[] pass;

    /**
     * Constructs a LogLevelFilter instance that filters log entries based on the provided
     * log level pass settings.
     *
     * @param name the name of the filter
     * @param pass an array of boolean values indicating whether each log level should pass the filter.
     *             The array must have a length equal to the number of LogLevel values.
     *             Each index corresponds to a specific log level in the order they are declared
     *             in the LogLevel enum.
     */
    public LogLevelFilter(String name, boolean[] pass) {
        this.name = name;
        this.pass = Arrays.copyOf(pass, LogLevel.values().length);
    }

    /**
     * Creates a LogLevelFilter that allows only the specified log level to pass.
     *
     * @param level the log level to match. Only this level will be allowed to pass through the filter.
     * @return a LogLevelFilter instance that matches the specified log level.
     */
    public static LogLevelFilter match(LogLevel level) {
        boolean[] levels = new boolean[LogLevel.values().length];
        levels[level.ordinal()] = true;
        return new LogLevelFilter("filter[level = " + level.name() + "]", levels);
    }

    /**
     * Creates a {@code LogLevelFilter} that blocks all log levels, effectively allowing none to pass.
     *
     * @return a {@code LogLevelFilter} instance configured with all log levels denied.
     */
    public static LogLevelFilter nonePass() {
        boolean[] levels = new boolean[LogLevel.values().length];
        return new LogLevelFilter("filter[nonePass]", levels);
    }

    /**
     * Creates a LogLevelFilter instance that allows all log levels to pass through the filter.
     *
     * @return A LogLevelFilter that permits all log levels without restriction.
     */
    public static LogLevelFilter allPass() {
        boolean[] levels = new boolean[LogLevel.values().length];
        Arrays.fill(levels, true);
        return new LogLevelFilter("filter[allPass]", levels);
    }

    /**
     * Creates a {@link LogLevelFilter} that allows log messages at the specified log level
     * and any higher levels to pass through.
     *
     * @param level the minimum log level that should pass the filter; log levels lower than
     *              this will be denied.
     * @return a new {@link LogLevelFilter} that filters log entries based on the specified log level.
     */
    public static LogLevelFilter pass(LogLevel level) {
        boolean[] levels = new boolean[LogLevel.values().length];
        Arrays.fill(levels, level.ordinal(), LogLevel.values().length, true);
        return new LogLevelFilter("filter[level >= " + level.name() + "]", levels);
    }

    /**
     * Creates a {@code LogLevelFilter} that denies logging for all log levels less than
     * the specified {@code level}. All log levels greater than or equal to the given
     * level will not pass the filter.
     *
     * @param level the log level below which all log entries are denied
     * @return a {@code LogLevelFilter} that denies log entries below the specified level
     */
    public static LogLevelFilter deny(LogLevel level) {
        boolean[] levels = new boolean[LogLevel.values().length];
        Arrays.fill(levels, 0, level.ordinal() + 1, true);
        return new LogLevelFilter("filter[level < " + level.name() + "]", levels);
    }

    @Override
    public LogLevelFilter copy() {
        return new LogLevelFilter(name, pass);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, CharSequence msg, @Nullable Throwable t) {
        return pass[lvl.ordinal()];
    }

    @Override
    public boolean isEnabled(String loggerName, LogLevel lvl, @Nullable String marker) {
        return pass[lvl.ordinal()];
    }

    @Override
    public boolean isLevelEnabled(LogLevel lvl) {
        return pass[lvl.ordinal()];
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof LogLevelFilter other)) return false;
        return name.equals(other.name) && Objects.deepEquals(pass, other.pass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, Arrays.hashCode(pass));
    }

    @Override
    public LogFilter andThen(LogFilter other) {
        if (this.equals(LogFilter.allPass())) return other;
        if (this.equals(LogFilter.nonePass())) return this;

        return LogFilter.super.andThen(other);
    }
}
