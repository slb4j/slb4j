/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slb4j.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slb4j.support.formatter.PatternTimeStampFormatter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeStampFormatterTest {

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
    void testPatterns(String pattern) {
        long timestamp = 1705574640000L; // 2024-01-18T10:44:00Z
        ZoneId zoneId = ZoneId.systemDefault();

        TimeStampFormatter formatter = PatternTimeStampFormatter.parse(pattern, zoneId);
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
    void testVariousTimestamps(long timestamp) {
        String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
        ZoneId zoneId = ZoneId.systemDefault();

        TimeStampFormatter formatter = PatternTimeStampFormatter.parse(pattern, zoneId);
        DateTimeFormatter stdFormatter = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);

        String expected = stdFormatter.format(Instant.ofEpochMilli(timestamp));
        String actual = formatter.toString(timestamp);

        assertEquals(expected, actual, "Timestamp " + timestamp + " failed");
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1705574640000L, // 2024-01-18T10:44:00Z (winter offset in Europe/Berlin: +01)
            1719836640000L  // 2024-07-01T10:24:00Z (summer offset in Europe/Berlin: +02)
    })
    void testDstHandlingInDstZone(long timestamp) {
        String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
        ZoneId zoneId = ZoneId.of("Europe/Berlin");

        TimeStampFormatter formatter = PatternTimeStampFormatter.parse(pattern, zoneId);
        DateTimeFormatter stdFormatter = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);

        String expected = stdFormatter.format(Instant.ofEpochMilli(timestamp));
        String actual = formatter.toString(timestamp);

        assertEquals(expected, actual, "DST handling failed for timestamp " + timestamp);
    }
}
