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
package org.slb4j.filter;

import org.jspecify.annotations.Nullable;
import org.slb4j.LogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class FiltersTest {

    @ParameterizedTest
    @CsvSource({
            "my.logger, my.logger, true",
            "my.logger, other.logger, false",
            "log.*, log.a, true",
            "log.*, other.a, false"
    })
    void testLoggerNameFilter(String pattern, String loggerName, boolean expected) {
        // Simple pattern matching for test: if pattern ends with *, use startsWith, else equals
        LoggerNameFilter filter = new LoggerNameFilter("test", name ->
                pattern.endsWith("*") ? name.startsWith(pattern.substring(0, pattern.length() - 1)) : name.equals(pattern)
        );

        assertEquals(expected, filter.isEnabled(loggerName, LogLevel.INFO, ""));
        assertEquals(expected, filter.test(System.currentTimeMillis(), loggerName, LogLevel.INFO, "", null, "msg", null));
    }

    @Test
    void testLoggerNamePrefixFilter() {
        LoggerNamePrefixFilter filter = new LoggerNamePrefixFilter("test");
        filter.setLevel("com.dua3", LogLevel.DEBUG);
        filter.setLevel("org.slb4j", LogLevel.TRACE);
        filter.setLevel("org.apache", LogLevel.WARN);

        // Global level check
        assertTrue(filter.isLevelEnabled(LogLevel.TRACE));

        // Prefix checks
        assertTrue(filter.isEnabled("com.dua3.MyClass", LogLevel.DEBUG, ""));
        assertFalse(filter.isEnabled("com.dua3.MyClass", LogLevel.TRACE, ""));

        assertTrue(filter.isEnabled("org.slb4j.MyClass", LogLevel.TRACE, ""));

        assertTrue(filter.isEnabled("org.apache.log4j.Logger", LogLevel.WARN, ""));
        assertFalse(filter.isEnabled("org.apache.log4j.Logger", LogLevel.INFO, ""));

        assertFalse(filter.isEnabled("other.package.Class", LogLevel.WARN, ""));
    }

    @ParameterizedTest
    @CsvSource({
            "MARKER, MARKER, true",
            "MARKER, OTHER, false",
            "MARKER, , false",
            " , , true",
            " , SOME, false"
    })
    void testMarkerFilter(@Nullable String filterMarker, @Nullable String logMarker, boolean expected) {
        MarkerFilter filter = new MarkerFilter("test", (filterMarker == null ? "" : filterMarker)::equals);

        assertEquals(expected, filter.isMarkerEnabled(logMarker));
        assertEquals(expected, filter.isEnabled("logger", LogLevel.INFO, logMarker));
        assertEquals(expected, filter.test(System.currentTimeMillis(), "logger", LogLevel.INFO, logMarker, null, "msg", null));
    }

    @ParameterizedTest
    @CsvSource({
            "hello, hello world, true",
            "hello, goodbye, false"
    })
    void testMessageTextFilter(String search, String message, boolean expected) {
        MessageTextFilter filter = new MessageTextFilter("test", msg -> msg.toString().contains(search));

        assertTrue(filter.isEnabled("logger", LogLevel.INFO, "")); // Message filter doesn't affect isEnabled usually
        assertEquals(expected, filter.test(System.currentTimeMillis(), "logger", LogLevel.INFO, "", null, message, null));
    }

    @Test
    void testCombinedFilter() {
        LogLevelFilter f1 = LogLevelFilter.pass(LogLevel.INFO);
        MarkerFilter f2 = new MarkerFilter("f2", "IMPORTANT"::equals);
        CombinedFilter combined = new CombinedFilter(f1, f2);

        assertTrue(combined.isEnabled("logger", LogLevel.INFO, "IMPORTANT"));
        assertFalse(combined.isEnabled("logger", LogLevel.DEBUG, "IMPORTANT"));
        assertFalse(combined.isEnabled("logger", LogLevel.INFO, "TRIVIAL"));

        assertTrue(combined.test(System.currentTimeMillis(), "logger", LogLevel.INFO, "IMPORTANT", null, "msg", null));
    }
}
