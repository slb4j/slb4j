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

import java.util.function.Supplier;

/**
 * The DefaultLogEntryFilter class is an implementation of the LogEntryFilter interface
 * that filters log entries based on their log level and a user-defined filter.
 *
 * <p>DefaultLogEntryFilter provides methods to set and retrieve the log level and filter,
 * as well as a test method to determine if a LogEntry should be included or excluded.
 */
public final class LogLevelFilter implements LogFilter {

    private final String name;
    private final LogLevel level;

    /**
     * Constructs a new DefaultLogEntryFilter with the specified log level and filter.
     *
     * @param name  the name of the filter
     * @param level the minimal level to let through
     */
    public LogLevelFilter(String name, LogLevel level) {
        this.name = name;
        this.level = level;
    }

    public LogLevel level() {
        return level;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, Supplier<@Nullable String> msg, @Nullable Throwable t) {
        return lvl.ordinal() >= level.ordinal();
    }

    @Override
    public boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
        return isLevelEnabled(logLevel);
    }

    @Override
    public boolean isLevelEnabled(LogLevel logLevel) {
        return logLevel.ordinal() >= level.ordinal();
    }
}
