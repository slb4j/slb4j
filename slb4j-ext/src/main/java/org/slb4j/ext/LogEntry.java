package org.slb4j.ext;

import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

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
     * @return an {@link Instant} representing the exact time the log event was recorded.
     */
    Instant time();

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
    @Nullable String message();

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
     *
     * @param time        The timestamp of when the log event occurred.
     * @param logger      The name of the logger that generated the log event.
     * @param level       The severity level of the log event, represented by {@link LogLevel}.
     * @param marker      An optional marker associated with the log event, providing additional categorization.
     * @param mdc         An optional Mapping Diagnostic Context (MDC) associated with the log event, providing
     *                    contextual information for diagnostic purposes.
     * @param location    An optional {@link Location} object providing details about the origin of the log event
     *                    within the code (e.g., class name, method name, file name, line number).
     * @param message     A supplier of the log message. The message is lazily evaluated, allowing computation
     *                    to be deferred until it is actually needed.
     * @param throwable   An optional throwable associated with the log event, representing an exception or error
     *                    related to the event.
     * @return an instance of LogEntry representing the log event with the given data.
     */
    static LogEntry of(
            Instant time,
            String logger,
            LogLevel level,
            @Nullable String marker,
            @Nullable MDC mdc,
            @Nullable Location location,
            @Nullable String message,
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
        Instant time,
        String logger,
        LogLevel level,
        @Nullable String marker,
        @Nullable MDC mdc,
        @Nullable Location location,
        @Nullable String message,
        @Nullable Throwable throwable
) implements LogEntry {
}
