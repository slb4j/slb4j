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
package org.slb4j.ext;

import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.jspecify.annotations.Nullable;

/**
 * The LogEntry record encapsulates information about a single log event.
 * It is an immutable and thread-safe representation of a log message
 * containing all relevant metadata associated with the log entry.
 *
 */
public interface LogEntry {
    /**
     * Retrieves the timestamp of when the log event occurred.
     *
     * @return the timestamp of the log event (milliseconds since epoch).
     */
    long time();

    /**
     * Retrieves the name of the logger that generated the log event.
     *
     * @return the name of the logger as a string.
     */
    String logger();

    /**
     * Retrieves the severity level of the log event.
     *
     * @return the log level associated with the log event, represented as a {@link LogLevel} enumeration.
     */
    LogLevel level();

    /**
     * Returns an optional marker associated with the log event.
     * The marker can provide additional categorization or contextual
     * information for the log entry, aiding in filtering or routing log messages.
     *
     * @return the marker associated with the log event, or null if no marker is present.
     */
    @Nullable String marker();

    /**
     * Retrieves the Mapping Diagnostic Context (MDC) associated with the log event.
     * The MDC provides contextual information that can help in diagnosing issues
     * or understanding the environment in which the log event occurred.
     *
     * @return an {@link MDC} object containing key-value pairs of contextual information,
     * or null if no MDC is associated with the log event.
     */
    @Nullable MDC mdc();

    /**
     * Retrieves the location information associated with the log entry.
     * The location provides details about the origin of the log event within the code,
     * such as the class name, method name, file name, and line number. This information
     * can be useful for understanding the precise context in which the log event occurred.
     *
     * @return an optional {@link Location} object containing the code context of the log event,
     *         or null if the location information is unavailable.
     */
    @Nullable Location location();

    /**
     * Retrieves the log message associated with this log entry.
     * The message provides additional information about the log event.
     * It might be null if no message was provided or applicable.
     *
     * @return the log message as a string, or null if not available.
     */
    String message();

    /**
     * Retrieves the throwable associated with the log entry, if present.
     * This throwable typically represents an exception or error
     * related to the log event.
     *
     * @return the throwable associated with the log event, or {@code null}
     *         if no throwable is associated.
     */
    @Nullable Throwable throwable();

    /**
     * Creates a new instance of a {@link LogEntry} with the specified properties.
     *
     * @param time      the timestamp of the log event in milliseconds since the epoch.
     * @param logger    the name of the logger that generated the log event.
     * @param level     the severity level of the log event, represented as a {@link LogLevel}.
     * @param marker    an optional marker associated with the log event, or null if no marker is present.
     * @param mdc       the Mapping Diagnostic Context (MDC) containing key-value pairs of contextual
     *                  information, or null if no MDC is associated with the log event.
     * @param location  the location information of the log event, such as the class, method,
     *                  file, and line number, or null if unavailable.
     * @param message   the textual message associated with the log event, or null if no message is provided.
     * @param throwable the throwable (e.g., an exception) associated with the log event, or null if none is present.
     * @return a new {@link LogEntry} instance representing the log event.
     */
    static LogEntry of(
            long time,
            String logger,
            LogLevel level,
            @Nullable String marker,
            @Nullable MDC mdc,
            @Nullable Location location,
            String message,
            @Nullable Throwable throwable
    ) {
        return new LogEntryRecord(
                time,
                logger,
                level,
                marker,
                mdc,
                location,
                message,
                throwable
        );
    }
}

record LogEntryRecord(
        long time,
        String logger,
        LogLevel level,
        @Nullable String marker,
        @Nullable MDC mdc,
        @Nullable Location location,
        @Nullable String message,
        @Nullable Throwable throwable
) implements LogEntry {
}
