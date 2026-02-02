package org.slb4j.layout;

import org.jspecify.annotations.Nullable;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.LogLayout;
import org.slb4j.MDC;
import org.slb4j.support.TimeStampFormatter;
import org.slb4j.support.Util;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

/**
 * The JsonLayout class implements the {@link LogLayout} interface and formats log entries into a JSON format.
 */
public final class JsonLayout implements LogLayout {
    private final TimeStampFormatter timeStampFormatter;

    /**
     * Constructs a new instance of the JsonLayout class for the given ZoneId.
     *
     * @param zoneId the time zone for formatting timestamps.
     */
    public JsonLayout(ZoneId zoneId) {
        this.timeStampFormatter = TimeStampFormatter.parse("yyyy-MM-dd HH:mm:ss,SSS", zoneId, Locale.getDefault());
    }

    @Override
    public String getType() {
        return StandardLayout.JSON.type();
    }

    @Override
    public void formatLogEntry(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
        app.append("{\"timestamp\":\"");
        timeStampFormatter.appendTo(timestamp, app);
        app.append("\",\"level\":\"");
        app.append(lvl.name());
        app.append("\",\"logger\":\"");
        appendJsonEscaped(app, loggerName);
        app.append("\",\"message\":\"");
        appendJsonEscaped(app, msg);
        app.append("\"");

        if (mrk != null) {
            app.append(",\"marker\":\"");
            appendJsonEscaped(app, mrk);
            app.append("\"");
        }

        if (mdc != null) {
            Map<String, String> contextMap = mdc.get();
            if (!contextMap.isEmpty()) {
                app.append(",\"mdc\":{");
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

        if (loc != null) {
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

        if (t != null) {
            app.append(",\"exception\":\"");
            StringBuilder sb = new StringBuilder();
            Util.appendStackTrace(sb, t);
            appendJsonEscaped(app, sb.toString());
            app.append("\"");
        }

        app.append("}\n");
    }

    private void appendJsonEscaped(Appendable app, @Nullable String s) throws IOException {
        if (s == null) {
            app.append("null");
            return;
        }

        for (int i = 0; i < s.length(); i++) {
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
    }
}
