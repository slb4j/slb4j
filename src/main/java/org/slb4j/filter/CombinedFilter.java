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

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The CombinedFilter class represents a composite implementation of the LogFilter interface,
 * allowing multiple filters to be combined into a single filter.
 * <p>
 * The composed filter only passes log entries if all the constituent filters allow them.
 */
public final class CombinedFilter implements LogFilter {
    private final String name;
    private final LogFilter[] filters;

    /**
     * Constructs a CombinedFilter instance by combining multiple LogFilter instances.
     * The resulting filter only passes log entries if all the given filters permit them.
     *
     * @param filters the array of LogFilter instances to be combined; each filter is applied sequentially
     */
    public CombinedFilter(LogFilter... filters) {
        this.name = Arrays.stream(filters).map(LogFilter::name).collect(Collectors.joining(",", "combined(", ")"));
        this.filters = filters;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean test(Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, Supplier<@Nullable String> msg, @Nullable Throwable t) {
        for (LogFilter filter : filters) {
            if (!filter.test(instant, loggerName, lvl, mrk, mdc, msg, t)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
        for (LogFilter filter : filters) {
            if (!filter.isEnabled(loggerName, logLevel, marker)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isLevelEnabled(LogLevel logLevel) {
        for (LogFilter filter : filters) {
            if (!filter.isLevelEnabled(logLevel)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isMarkerEnabled(@Nullable String marker) {
        for (LogFilter filter : filters) {
            if (!filter.isMarkerEnabled(marker)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public LogFilter firstApply(LogFilter filter) {
        if (Objects.equals(filter, LogFilter.allPass())) return this;
        if (Objects.equals(filter, LogFilter.nonePass())) return filter;

        LogFilter[] newFilters = new LogFilter[filters.length + 1];
        newFilters[0] = filter;
        for (int i = 0; i < filters.length; i++) {
            newFilters[i + 1] = filters[i];
        }
        return new CombinedFilter(newFilters);
    }

    @Override
    public LogFilter andThen(LogFilter other) {
        if (Objects.equals(other, LogFilter.allPass())) return this;
        if (Objects.equals(other, LogFilter.nonePass())) return other;

        LogFilter[] newFilters = new LogFilter[filters.length + 1];
        for (int i = 0; i < filters.length; i++) {
            newFilters[i] = filters[i];
        }
        newFilters[newFilters.length - 1] = other;
        return new CombinedFilter(newFilters);
    }
}
