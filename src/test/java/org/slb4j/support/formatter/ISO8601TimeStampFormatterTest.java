package org.slb4j.support.formatter;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ISO8601TimeStampFormatterTest {

    @Test
    void testFormatWithUTC() throws IOException {
        ISO8601TimeStampFormatter formatter = new ISO8601TimeStampFormatter('T', '.', true, ZoneId.of("UTC"));
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L; // 2024-01-31 04:00:00 UTC
        formatter.appendTo(timestamp, sb);
        assertEquals("2024-01-31T04:00:00.000Z", sb.toString());
    }

    @Test
    void testFormatWithOffset() throws IOException {
        ISO8601TimeStampFormatter formatter = new ISO8601TimeStampFormatter('T', '.', true, ZoneId.of("GMT+02:00"));
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L; // 2024-01-31 04:00:00 UTC -> 2024-01-31 06:00:00 GMT+02
        formatter.appendTo(timestamp, sb);
        assertEquals("2024-01-31T06:00:00.000+02:00", sb.toString());
    }

    @Test
    void testFormatWithNegativeOffset() throws IOException {
        ISO8601TimeStampFormatter formatter = new ISO8601TimeStampFormatter('T', '.', true, ZoneId.of("GMT-05:00"));
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L; // 2024-01-31 04:00:00 UTC -> 2024-01-30 23:00:00 GMT-05
        formatter.appendTo(timestamp, sb);
        assertEquals("2024-01-30T23:00:00.000-05:00", sb.toString());
    }
}
