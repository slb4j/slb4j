package org.slb4j.support;

import org.slb4j.support.formatter.ISO8601TimeStampFormatter;
import org.slb4j.support.formatter.MillisTimeStampFormatter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;

public interface TimeStampFormatter {
    TimeStampFormatter DEFAULT_FORMATTER = new ISO8601TimeStampFormatter(' ', ',', ZoneId.systemDefault());
    TimeStampFormatter ISO8601_FORMATTER = new ISO8601TimeStampFormatter('T', '.', ZoneId.systemDefault());
    TimeStampFormatter MILLIS_FORMATTER = new MillisTimeStampFormatter();

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
