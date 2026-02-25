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

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The LoggerNameFilter filters log entries based on their logger name and a user-defined filter.
 */
public final class LoggerNameFilter implements LogFilter {

    private final String name;
    private final Predicate<? super String> predicate;

    /**
     * Constructs a new DefaultLogEntryFilter with the specified log level and filter.
     *
     * @param name  the name of the filter
     * @param predicate the filter to set for the logger name
     */
    public LoggerNameFilter(String name, Predicate<? super String> predicate) {
        this.name = name;
        this.predicate = predicate;
    }

    @Override
    public LoggerNameFilter copy() {
        return new LoggerNameFilter(name, predicate);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, String msg, @Nullable Throwable t) {
        return predicate.test(loggerName);
    }

    @Override
    public boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
        return predicate.test(loggerName);
    }
    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof LoggerNameFilter other)) return false;
        return name.equals(other.name) && predicate.equals(other.predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, predicate);
    }
}
