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

/**
 * The MessageTextFilter class is an implementation of the LogFilter interface
 * that filters log entries based on their message content.
 */
public final class MessageTextFilter implements LogFilter {

    private final String name;
    private final Predicate<? super CharSequence> textFilter;

    /**
     * Constructs a new MessageTextFilter with the specified name and predicate.
     *
     * @param name  the name of this filter
     * @param textFilter the predicate to test the message content against
     */
    public MessageTextFilter(String name, Predicate<? super CharSequence> textFilter) {
        this.name = name;
        this.textFilter = textFilter;
    }

    @Override
    public MessageTextFilter copy() {
        return new MessageTextFilter(name, textFilter);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, CharSequence msg, @Nullable Throwable t) {
        return textFilter.test(msg);
    }

    @Override
    public boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
        return true;
    }
    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof MessageTextFilter other)) return false;
        return name.equals(other.name) && textFilter.equals(other.textFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, textFilter);
    }
}
