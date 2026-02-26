package org.slb4j.support;

import org.slb4j.support.formatter.ISO8601TimeStampFormatter;
import org.slb4j.support.formatter.MillisTimeStampFormatter;
import org.slb4j.support.formatter.TimeOnlyTimeStampFormatter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Defines a contract for formatting timestamps represented as epoch milliseconds
 * into human-readable string representations or appending them to an output target.
 * This interface provides both performance-optimized and debug-friendly methods
 * for working with timestamp data.
 */
public interface TimeStampFormatter {
    /**
     * A preconfigured instance of {@code ISO8601TimeStampFormatter} for formatting timestamps
     * in the ISO 8601 standard format using the UTC time zone.
     */
    TimeStampFormatter ISO8601_FORMATTER = new ISO8601TimeStampFormatter('T', '.', true, ZoneOffset.UTC);
    /**
     * A preconfigured instance of {@code ISO8601TimeStampFormatter} for formatting timestamps
     * in the ISO 8601 standard format using the system default time zone.
     */
    TimeStampFormatter ISO8601_FORMATTER_LOCAL_ZONE = new ISO8601TimeStampFormatter('T', '.', true, ZoneId.systemDefault());
    /**
     * Default {@link TimeStampFormatter} implementation that formats timestamps in a human-readable
     * ISO 8601-like pattern with a space (' ') between date and time, and a comma (',') separating
     * seconds and milliseconds. The timestamps are formatted based on the UTC time zone.
     */
    TimeStampFormatter DEFAULT_FORMATTER = new ISO8601TimeStampFormatter(' ', ',', true, ZoneOffset.UTC);
    /**
     * Default {@link TimeStampFormatter} implementation that formats timestamps in a human-readable
     * ISO 8601-like pattern with a space (' ') between date and time, and a comma (',') separating
     * seconds and milliseconds. The timestamps are formatted based on the system default time zone.
     */
    TimeStampFormatter DEFAULT_FORMATTER_LOCAL_ZONE = new ISO8601TimeStampFormatter(' ', ',', true, ZoneId.systemDefault());
    /**
     * A predefined instance of {@link MillisTimeStampFormatter} used to format
     * timestamps by directly representing the epoch milliseconds as a string.
     */
    TimeStampFormatter MILLIS_FORMATTER_LOCAL_ZONE = new MillisTimeStampFormatter();
    /**
     * A preconfigured instance of {@code ISO8601TimeStampFormatter} for formatting timestamps
     * without date.
     */
    TimeStampFormatter TIME_FORMATTER = new TimeOnlyTimeStampFormatter('.', true, ZoneOffset.UTC);

    /**
     * Appends a formatted representation of the given timestamp to the provided {@code Appendable}.
     * The timestamp is expected to be in epoch milliseconds, and it is formatted based on
     * the internal configuration of the implementing formatter.
     *
     * @param timestamp the epoch timestamp in milliseconds to be formatted and appended.
     * @param app the {@code Appendable} to which the formatted timestamp will be written.
     * @throws IOException if an I/O error occurs while appending to the {@code Appendable}.
     */
    void appendTo(long timestamp, Appendable app) throws IOException;

    /**
     * Converts the given timestamp to its corresponding string representation.
     * The method formats the timestamp using a builder-like approach, relying on
     * internally configured components such as pre-defined formatting rules and
     * time zone or offset settings.
     * <p>
     * This method is intended for debugging purposes only (it is not garbage-free,
     * i.e., when used for logging might create a bottleneck).
     *
     * @param timestamp the epoch timestamp in milliseconds that is to be formatted
     *                  into a string representation. It is interpreted based on
     *                  the configured time zone and offsets.
     * @return the string representation of the provided timestamp, formatted
     * according to the internal configuration of this formatter.
     * @throws UncheckedIOException if an I/O error occurs while constructing
     *                              the formatted string.
     */
    default String toString(long timestamp) {
        try {
            StringBuilder sb = new StringBuilder(32);
            appendTo(timestamp, sb);
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Internal error", e);
        }
    }

}
