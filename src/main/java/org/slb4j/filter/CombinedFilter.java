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
    public CombinedFilter copy() {
        LogFilter[] copy = new LogFilter[filters.length];
        for (int i = 0; i < filters.length; i++) {
            copy[i] = filters[i].copy();
        }
        return new CombinedFilter(copy);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, CharSequence msg, @Nullable Throwable t) {
        for (int i = 0; i < filters.length; i++) {
            if (!filters[i].test(timestamp, loggerName, lvl, mrk, mdc, msg, t)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
        for (int i = 0; i < filters.length; i++) {
            if (!filters[i].isEnabled(loggerName, logLevel, marker)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isLevelEnabled(LogLevel logLevel) {
        for (int i = 0; i < filters.length; i++) {
            if (!filters[i].isLevelEnabled(logLevel)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isMarkerEnabled(@Nullable String marker) {
        for (int i = 0; i < filters.length; i++) {
            if (!filters[i].isMarkerEnabled(marker)) {
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
        System.arraycopy(filters, 0, newFilters, 1, filters.length);
        return new CombinedFilter(newFilters);
    }

    @Override
    public LogFilter andThen(LogFilter other) {
        if (Objects.equals(other, LogFilter.allPass())) return this;
        if (Objects.equals(other, LogFilter.nonePass())) return other;

        LogFilter[] newFilters = new LogFilter[filters.length + 1];
        System.arraycopy(filters, 0, newFilters, 0, filters.length);
        newFilters[newFilters.length - 1] = other;
        return new CombinedFilter(newFilters);
    }
    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof  CombinedFilter other)) return false;
        return name.equals(other.name) && Objects.deepEquals(filters, other.filters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, Arrays.hashCode(filters));
    }
}
