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

import org.slb4j.filter.LoggerNamePrefixFilter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Represents a functional interface for handling log entries.
 */
public interface LogHandler {

    /**
     * Retrieves the name of the log entry handler.
     *
     * @return the name of the log entry handler as a String
     */
    String name();

    /**
     * Determines whether logging is enabled for a specific log level.
     *
     * @param lvl the log level to check
     * @return {@code true} if logging is enabled for the specified log level, otherwise {@code false}
     */
    default boolean isEnabled(LogLevel lvl) {
        return !(getFilter() instanceof LoggerNamePrefixFilter filter) || filter.getLevel().ordinal() <= lvl.ordinal();
    }

    /**
     * Handles a log entry.
     *
     * @param instant    the timestamp of the log entry
     * @param loggerName the name of the logger
     * @param lvl        the log level of the log entry
     * @param mrk        the marker associated with the log entry, or {@code null} if none
     * @param mdc        the MDC associated with the log entry
     * @param loc        the location resolver used to resolve the location of the log entry call site
     * @param msg        the message of the log entry
     * @param t          the throwable associated with the log entry, or {@code null} if none
     */
    void handle(Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, Supplier<String> msg, @Nullable Throwable t);

    /**
     * Sets the filter to be used for determining which log entries should be processed.
     *
     * @param filter the filter used to include or exclude log entries; must not be {@code null}
     */
    void setFilter(LogFilter filter);

    /**
     * Retrieves the current filter used for determining which log entries should be processed.
     *
     * @return the LogEntryFilter that is currently set; never {@code null}
     */
    LogFilter getFilter();
}
