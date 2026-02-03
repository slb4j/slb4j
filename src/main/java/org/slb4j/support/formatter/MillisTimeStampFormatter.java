package org.slb4j.support.formatter;

import org.slb4j.support.TimeStampFormatter;

import java.io.IOException;

/**
 * A {@link TimeStampFormatter} implementation that just writes out the millis as received.
 */
public final class MillisTimeStampFormatter implements TimeStampFormatter {

    /**
     * Default constructor.
     */
    public MillisTimeStampFormatter() {
        // nothing to do
    }

    @Override
    public void appendTo(long timestamp, Appendable app) throws IOException {
        app.append(Long.toString(timestamp));
    }

    @Override
    public String toString(long timestamp) {
        return Long.toString(timestamp);
    }
}
