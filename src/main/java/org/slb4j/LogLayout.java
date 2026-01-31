package org.slb4j;

import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * The LogPattern interface defines the methods required to format log entries.
 */
public interface LogLayout {
    /**
     * Returns the type of the log pattern.
     * @return the type
     */
    String getType();

    /**
     * Returns the text of the log pattern.
     * @return the text
     */
    default String getText() { return ""; }

    /**
     * Returns whether location information is needed for this log pattern.
     * @return true if location information is needed, false otherwise
     */
    default boolean isLocationNeeded() { return false; }

    /**
     * Returns the header for the log pattern.
     * @return the header
     */
    default String getHeader() { return ""; }

    /**
     * Returns the footer for the log pattern.
     * @return the footer
     */
    default String getFooter() { return ""; }

    /**
     * Formats a log entry.
     * @param app the appendable to write to
     * @param timestamp the timestamp
     * @param loggerName the logger name
     * @param lvl the log level
     * @param mrk the marker
     * @param mdc the MDC
     * @param loc the location resolver
     * @param msg the message
     * @param t the throwable
     * @param consoleCodes the console codes
     * @throws IOException if an I/O error occurs
     */
    void formatLogEntry(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException;
}
