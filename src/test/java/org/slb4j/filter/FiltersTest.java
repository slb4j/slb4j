package org.slb4j.filter;

import org.slb4j.LogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

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
        assertEquals(expected, filter.test(Instant.now(), loggerName, LogLevel.INFO, "", null, () -> "msg", null));
    }

    @Test
    void testLoggerNamePrefixFilter() {
        LoggerNamePrefixFilter filter = new LoggerNamePrefixFilter("test");
        filter.setLevel(LogLevel.TRACE); // Set global to TRACE to test specific levels
        filter.setLevel("com.dua3", LogLevel.DEBUG);
        filter.setLevel("slb4j", LogLevel.TRACE);
        filter.setLevel("org.apache", LogLevel.WARN);

        // Global level check
        assertTrue(filter.isLevelEnabled(LogLevel.TRACE));

        // Prefix checks
        assertTrue(filter.isEnabled("com.dua3.MyClass", LogLevel.DEBUG, ""));
        assertFalse(filter.isEnabled("com.dua3.MyClass", LogLevel.TRACE, ""));

        assertTrue(filter.isEnabled("org.slb4j.MyClass", LogLevel.TRACE, ""));

        assertTrue(filter.isEnabled("org.apache.log4j.Logger", LogLevel.WARN, ""));
        assertFalse(filter.isEnabled("org.apache.log4j.Logger", LogLevel.INFO, ""));

        assertTrue(filter.isEnabled("other.package.Class", LogLevel.TRACE, ""));
    }

    @ParameterizedTest
    @CsvSource({
            "MARKER, MARKER, true",
            "MARKER, OTHER, false",
            "MARKER, , false",
            " , , true",
            " , SOME, false"
    })
    void testMarkerFilter(String filterMarker, String logMarker, boolean expected) {
        MarkerFilter filter = new MarkerFilter("test", (filterMarker == null ? "" : filterMarker)::equals);

        assertEquals(expected, filter.isMarkerEnabled(logMarker));
        assertEquals(expected, filter.isEnabled("logger", LogLevel.INFO, logMarker));
        assertEquals(expected, filter.test(Instant.now(), "logger", LogLevel.INFO, logMarker, null, () -> "msg", null));
    }

    @ParameterizedTest
    @CsvSource({
            "hello, hello world, true",
            "hello, goodbye, false"
    })
    void testMessageTextFilter(String search, String message, boolean expected) {
        MessageTextFilter filter = new MessageTextFilter("test", msg -> msg.contains(search));

        assertTrue(filter.isEnabled("logger", LogLevel.INFO, "")); // Message filter doesn't affect isEnabled usually
        assertEquals(expected, filter.test(Instant.now(), "logger", LogLevel.INFO, "", null, () -> message, null));
    }

    @Test
    void testCombinedFilter() {
        LogLevelFilter f1 = new LogLevelFilter("f1", LogLevel.INFO);
        MarkerFilter f2 = new MarkerFilter("f2", "IMPORTANT"::equals);
        CombinedFilter combined = new CombinedFilter(f1, f2);

        assertTrue(combined.isEnabled("logger", LogLevel.INFO, "IMPORTANT"));
        assertFalse(combined.isEnabled("logger", LogLevel.DEBUG, "IMPORTANT"));
        assertFalse(combined.isEnabled("logger", LogLevel.INFO, "TRIVIAL"));

        assertTrue(combined.test(Instant.now(), "logger", LogLevel.INFO, "IMPORTANT", null, () -> "msg", null));
    }
}
