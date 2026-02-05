package org.slb4j.layout;

import org.jspecify.annotations.Nullable;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.LogLayout;
import org.slb4j.MDC;
import org.slb4j.support.formatter.ISO8601TimeStampFormatter;
import org.slb4j.support.TimeStampFormatter;
import org.slb4j.support.Util;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * The YamlLayout class implements the {@link LogLayout} interface and formats log entries into a YAML format.
 */
public final class YamlLayout implements LogLayout {

    private static final class SingletonHolder {
        static final YamlLayout INSTANCE = new YamlLayout(ZoneOffset.UTC);
    }

    /**
     * Return the singleton instance for this {@link LogLayout}.
     *
     * @return the singleton instance of YamlLayout
     */
    public static YamlLayout instance() {
        return YamlLayout.SingletonHolder.INSTANCE;
    }

    private final TimeStampFormatter timeStampFormatter;

    /**
     * Constructs a new instance of the YamlLayout class for the given ZoneId.
     *
     * @param zoneId the time zone identifier for formatting timestamps
     */
    public YamlLayout(ZoneId zoneId) {
        this.timeStampFormatter = new ISO8601TimeStampFormatter('T', '.', true, zoneId);
    }

    @Override
    public String getType() {
        return StandardLayout.YAML.type();
    }

    @Override
    public void formatLogEntry(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
        app.append("---\n");
        app.append("timestamp: \"");
        timeStampFormatter.appendTo(timestamp, app);
        app.append("\"\nlevel: \"");
        app.append(lvl.name());
        app.append("\"\nlogger: \"");
        appendYamlEscaped(app, loggerName);
        app.append("\"\nmessage: \"");
        appendYamlEscaped(app, msg);
        app.append("\"\n");

        if (mrk != null) {
            app.append("marker: \"");
            appendYamlEscaped(app, mrk);
            app.append("\"\n");
        }

        if (mdc != null) {
            Map<String, String> contextMap = mdc.get();
            if (!contextMap.isEmpty()) {
                app.append("mdc:\n");
                for (Map.Entry<String, String> entry : contextMap.entrySet()) {
                    app.append("  ");
                    appendYamlEscaped(app, entry.getKey());
                    app.append(": \"");
                    appendYamlEscaped(app, entry.getValue());
                    app.append("\"\n");
                }
            }
        }

        if (loc != null) {
            app.append("location:\n");
            app.append("  class: \"");
            appendYamlEscaped(app, loc.getClassName());
            app.append("\"\n  method: \"");
            appendYamlEscaped(app, loc.getMethodName());
            app.append("\"\n  file: \"");
            appendYamlEscaped(app, loc.getFileName());
            app.append("\"\n  line: ");
            app.append(String.valueOf(loc.getLineNumber()));
            app.append("\n");
        }

        if (t != null) {
            app.append("exception: \"");
            StringBuilder sb = new StringBuilder();
            Util.appendStackTrace(sb, t);
            appendYamlEscaped(app, sb.toString());
            app.append("\"\n");
        }
    }

    private static void appendYamlEscaped(Appendable app, @Nullable String s) throws IOException {
        if (s == null) {
            app.append("null");
            return;
        }

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> app.append("\\\"");
                case '\\' -> app.append("\\\\");
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
