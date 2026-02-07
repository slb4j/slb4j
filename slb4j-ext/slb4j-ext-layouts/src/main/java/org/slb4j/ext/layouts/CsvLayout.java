package org.slb4j.ext.layouts;

import org.jspecify.annotations.Nullable;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.LogLayout;
import org.slb4j.MDC;
import org.slb4j.support.TimeStampFormatter;

import java.io.IOException;

/**
 * The CsvLogPattern class implements the {@link LogLayout} interface and formats log entries into a CSV-compatible format.
 * Each log entry is represented as a single line in CSV format, with fields enclosed in double quotes and properly escaped.
 * <p>
 * The following fields are included in each log entry:
 * <ul>
 * <li>Timestamp in "yyyy-MM-dd HH:mm:ss,SSS" format.
 * <li>Log level.
 * <li>Logger name.
 * <li>Message, with special characters and quotes properly escaped.
 * </ul>
 * This class assumes a fixed CSV structure and does not include fields like marker, MDC, location, or throwable details.
 */
public final class CsvLayout implements LogLayout {

    private static final class SingletonHolder {
        static final CsvLayout INSTANCE = new CsvLayout();
    }

    /**
     * Return the singleton instance for this {@link LogLayout}.
     *
     * @return the singleton instance of CsvLayout
     */
    public static CsvLayout instance() {
        return SingletonHolder.INSTANCE;
    }

    private final TimeStampFormatter timeStampFormatter;

    /**
     * Constructs a new instance of the CsvLogPattern class for the given ZoneId.
     */
    public CsvLayout() {
        this.timeStampFormatter = TimeStampFormatter.ISO8601_FORMATTER;
    }

    @Override
    public String getType() {
        return "CsvLayout";
    }

    @Override
    public void formatLogEntry(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
        app.append('"');
        timeStampFormatter.appendTo(timestamp, app);
        app.append("\",\"");
        app.append(lvl.name());
        app.append("\",\"");
        appendCsvEscaped(app, loggerName);
        app.append("\",\"");
        appendCsvEscaped(app, msg);
        app.append("\"\n");
    }

    /**
     * Escapes a string for safe inclusion in a CSV file by replacing double quotes with two double quotes.
     * If the input string is null, appends the text "null" to the given appendable.
     *
     * @param app the Appendable to which the escaped string will be appended
     * @param msg the input string to be CSV-escaped; may be null
     * @throws IOException if an I/O error occurs while appending to the appendable
     */
    private static void appendCsvEscaped(Appendable app, @Nullable String msg) throws IOException {
        if (msg == null) {
            app.append("null");
            return;
        }

        int start = 0;
        int end;
        while ((end = msg.indexOf('"', start)) != -1) {
            app.append(msg, start, end);
            app.append("\"\"");
            start = end + 1;
        }
        app.append(msg, start, msg.length());
    }
}
