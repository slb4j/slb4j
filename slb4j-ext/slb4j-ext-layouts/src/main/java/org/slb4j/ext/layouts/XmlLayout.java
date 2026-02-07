package org.slb4j.ext.layouts;

import org.jspecify.annotations.Nullable;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogLayout;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.support.formatter.ISO8601TimeStampFormatter;
import org.slb4j.support.TimeStampFormatter;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The XmlLogPattern class implements the {@link LogLayout} interface and formats log entries into an XML format.
 */
public final class XmlLayout implements LogLayout {

    private static final class SingletonHolder {
        static final XmlLayout INSTANCE = new XmlLayout(ZoneOffset.UTC);
    }

    /**
     * Return the singleton instance for this {@link LogLayout}.
     *
     * @return the singleton instance of XmlLayout
     */
    public static XmlLayout instance() {
        return XmlLayout.SingletonHolder.INSTANCE;
    }

    private final TimeStampFormatter timeStampFormatter;

    /**
     * Constructs a new instance of the XmlLogPattern class for the given ZoneId.
     *
     * @param zoneId the time zone identifier for formatting timestamps
     */
    public XmlLayout(ZoneId zoneId) {
        this.timeStampFormatter = new ISO8601TimeStampFormatter('T', '.', true, zoneId);
    }

    @Override
    public String getType() {
        return "XmlLayout";
    }

    @Override
    public String getHeader() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<logEvents>\n";
    }

    @Override
    public String getFooter() {
        return "</logEvents>\n";
    }

    @Override
    public void formatLogEntry(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
        app.append("""
            <logEvent>
              <timestamp>""");
        timeStampFormatter.appendTo(timestamp, app);
        app.append("""
            </timestamp>
              <level>""");
        app.append(lvl.name());
        app.append("""
            </level>
              <logger>""");
        appendXmlEscaped(app, loggerName);
        app.append("""
            </logger>
              <message>""");
        appendXmlEscaped(app, msg);
        app.append("""
            </message>
            </logEvent>
            """);
    }

    /**
     * Escapes a given string for safe inclusion in XML by converting special characters into their corresponding
     * XML entities.
     *
     * @param app the {@code Appendable} to which the escaped string will be appended
     * @param s the input string to be XML-escaped; may be null
     * @throws IOException if an I/O error occurs while appending to the {@code Appendable}
     */
    private static void appendXmlEscaped(Appendable app, @Nullable String s) throws IOException {
        if (s == null) {
            app.append("null");
            return;
        }

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> app.append("&lt;");
                case '>' -> app.append("&gt;");
                case '&' -> app.append("&amp;");
                case '"' -> app.append("&quot;");
                case '\'' -> app.append("&apos;");
                default -> {
                    if (c < 32 && c != '\t' && c != '\n' && c != '\r') {
                        app.append("&#").append(Integer.toString(c)).append(";");
                    } else {
                        app.append(c);
                    }
                }
            }
        }
    }
}
