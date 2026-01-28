package org.slb4j.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimeStampFormatterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy/MM/dd",
            "HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "S",
            "SS",
            "SSS",
            "s",
            "ss",
            "m",
            "mm",
            "H",
            "HH",
            "d",
            "dd",
            "M",
            "MM",
            "y",
            "yy",
            "yyyy",
            "MM-dd-yyyy HH:mm:ss"
    })
    public void testPatterns(String pattern) {
        long timestamp = 1705574640000L; // 2024-01-18T10:44:00Z
        ZoneId zoneId = ZoneId.systemDefault();

        TimeStampFormatter formatter = TimeStampFormatter.parse(pattern, zoneId);
        DateTimeFormatter stdFormatter = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);

        String expected = stdFormatter.format(Instant.ofEpochMilli(timestamp));
        String actual = formatter.toString(timestamp);

        assertEquals(expected, actual, "Pattern '" + pattern + "' failed");
    }

    @ParameterizedTest
    @ValueSource(longs = {
            0L, // Epoch
            1705574640000L, // Some date in 2024
            1737194640000L, // Some date in 2025
            1609459200000L, // 2021-01-01
            -31536000000L,  // 1969-01-01
    })
    public void testVariousTimestamps(long timestamp) {
        String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
        ZoneId zoneId = ZoneId.systemDefault();

        TimeStampFormatter formatter = TimeStampFormatter.parse(pattern, zoneId);
        DateTimeFormatter stdFormatter = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);

        String expected = stdFormatter.format(Instant.ofEpochMilli(timestamp));
        String actual = formatter.toString(timestamp);

        assertEquals(expected, actual, "Timestamp " + timestamp + " failed");
    }
}
