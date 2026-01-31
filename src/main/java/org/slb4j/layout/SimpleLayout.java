package org.slb4j.layout;

import org.jspecify.annotations.Nullable;
import org.slb4j.ConsoleCode;
import org.slb4j.LocationResolver;
import org.slb4j.LogLayout;
import org.slb4j.LogLevel;
import org.slb4j.MDC;

import java.io.IOException;

/**
 * A simple layout that just outputs the log level and message with a newline.
 */
public class SimpleLayout implements LogLayout {

    /**
     * Default constructor.
     */
    public SimpleLayout() { /* nothing to do */ }

    @Override
    public String getType() {
        return StandardLayout.LOG4J_SIMPLE_LAYOUT.type();
    }

    @Override
    public void formatLogEntry(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, @Nullable String msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
        app.append(lvl.name()).append(" - ").append(msg).append('\n');
    }
}
