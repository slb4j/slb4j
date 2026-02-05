package org.slb4j.layout;

import org.jspecify.annotations.Nullable;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.LogLayout;
import org.slb4j.MDC;
import org.slb4j.support.Util;

import java.io.IOException;
import java.util.Map;

/**
 * The JsonLayout class implements the {@link LogLayout} interface and formats log entries into a JSON format.
 */
public final class JsonLayout implements LogLayout {

    private final boolean propertiesEnabled;
    private final boolean locationInfoEnabled;
    private final boolean stacktraceEnabled;
    private final int maxStringLength;
    private final String truncatedStringSuffix;

    /**
     * Constructs an instance of the JsonLayout class with the specified configuration parameters.
     *
     * @param propertiesEnabled whether context information (MDC) should be included in the JSON log output.
     * @param locationInfoEnabled whether location information (source file and line number) should be included in the JSON log output.
     * @param stacktraceEnabled whether stack trace details should be included in the JSON log output if a throwable is present.
     * @param maxStringLength the maximum length for string fields in the JSON log output; strings exceeding this length will be truncated.
     * @param truncatedStringSuffix the suffix appended to truncated strings to indicate truncation.
     */
    public JsonLayout(boolean propertiesEnabled, boolean locationInfoEnabled, boolean stacktraceEnabled, int maxStringLength, String truncatedStringSuffix) {
        this.propertiesEnabled = propertiesEnabled;
        this.locationInfoEnabled = locationInfoEnabled;
        this.stacktraceEnabled = stacktraceEnabled;
        this.maxStringLength = maxStringLength;
        this.truncatedStringSuffix = truncatedStringSuffix;
    }

    @Override
    public String getType() {
        return StandardLayout.JSON.type();
    }

    @Override
    public void formatLogEntry(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
        app.append("\"thread\":\"").append(Thread.currentThread().getName());
        app.append("\",\"level\":\"").append(lvl.name());
        app.append("\",\"loggerName\":\"");
        app.append("\",\"message\":\"");
        appendJsonEscaped(app, msg);
        app.append("\", \"endOfBatch\":false");
        app.append(",\"instant\":{\"epochSecond\":").append(Long.toString(timestamp/1000));
        app.append(",\"nanoOfSecond\":\"").append(Long.toString(timestamp%1000)).append("000000");
        app.append("\"}");

        // Appends marker map if present
        if (mrk != null) {
            app.append(",\"marker\":\"");
            appendJsonEscaped(app, mrk);
            app.append("\"");
        }

        // Appends context map if present
        if (propertiesEnabled && mdc != null) {
            Map<String, String> contextMap = mdc.get();
            if (!contextMap.isEmpty()) {
                app.append(",\"contextMap\":{");
                boolean first = true;
                for (Map.Entry<String, String> entry : contextMap.entrySet()) {
                    if (!first) {
                        app.append(",");
                    }
                    app.append("\"");
                    appendJsonEscaped(app, entry.getKey());
                    app.append("\":\"");
                    appendJsonEscaped(app, entry.getValue());
                    app.append("\"");
                    first = false;
                }
                app.append("}");
            }
        }

        // Appends location details if present: class, method, file, line
        if (locationInfoEnabled && loc != null) {
            app.append(",\"location\":{");
            app.append("\"class\":\"");
            appendJsonEscaped(app, loc.getClassName());
            app.append("\",\"method\":\"");
            appendJsonEscaped(app, loc.getMethodName());
            app.append("\",\"file\":\"");
            appendJsonEscaped(app, loc.getFileName());
            app.append("\",\"line\":");
            app.append(String.valueOf(loc.getLineNumber()));
            app.append("}");
        }

        // Appends exception details if present
        if (t != null) {
            app.append(",\"thrown\":\"{\"localizedMessage\": \"");
            appendJsonEscaped(app, t.getLocalizedMessage());
            app.append("\",\"message\": \"");
            appendJsonEscaped(app, t.getMessage());
            app.append("\",\"name\": \"");
            appendJsonEscaped(app, t.getClass().getName());
            app.append("\"");

            if (stacktraceEnabled) {
                app.append(",\"stacktrace\":\"");
                StringBuilder sb = new StringBuilder();
                Util.appendStackTrace(sb, t);
                appendJsonEscaped(app, sb.toString());
                app.append("\"");
            }
        }

        app.append("}\n");
    }

    /**
     * Appends a JSON-escaped representation of the given string to the specified {@link Appendable}.
     * Special characters in the string are escaped to ensure it is safe for inclusion in JSON.
     * If the input string is null, the text "null" is appended to the output.
     * Strings exceeding the maximum allowed length are truncated and suffixed accordingly.
     *
     * @param app the {@link Appendable} to which the JSON-escaped string will be appended
     * @param s the input string to be JSON-escaped; may be null
     * @throws IOException if an I/O error occurs while appending to the appendable
     */
    private void appendJsonEscaped(Appendable app, @Nullable String s) throws IOException {
        if (s == null) {
            app.append("null");
            return;
        }

        int length = Math.min(s.length(), maxStringLength);
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> app.append("\\\"");
                case '\\' -> app.append("\\\\");
                case '/' -> app.append("\\/");
                case '\b' -> app.append("\\b");
                case '\f' -> app.append("\\f");
                case '\n' -> app.append("\\n");
                case '\r' -> app.append("\\r");
                case '\t' -> app.append("\\t");
                default -> {
                    if (c < 32) {
                        app.append(String.format("\\u%04x", (int) c));
                    } else {
                        app.append(c);
                    }
                }
            }
        }

        if (length < s.length()) {
            app.append(truncatedStringSuffix);
        }
    }
}
