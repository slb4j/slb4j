package org.slb4j.filter;

import org.slb4j.LogLevel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogLevelFilterTest {

    @ParameterizedTest
    @CsvSource({
            "TRACE, TRACE, true",
            "TRACE, DEBUG, true",
            "TRACE, INFO,  true",
            "TRACE, WARN,  true",
            "TRACE, ERROR, true",
            "DEBUG, TRACE, false",
            "DEBUG, DEBUG, true",
            "DEBUG, INFO,  true",
            "DEBUG, WARN,  true",
            "DEBUG, ERROR, true",
            "INFO,  TRACE, false",
            "INFO,  DEBUG, false",
            "INFO,  INFO,  true",
            "INFO,  WARN,  true",
            "INFO,  ERROR, true",
            "WARN,  TRACE, false",
            "WARN,  DEBUG, false",
            "WARN,  INFO,  false",
            "WARN,  WARN,  true",
            "WARN,  ERROR, true",
            "ERROR, TRACE, false",
            "ERROR, DEBUG, false",
            "ERROR, INFO,  false",
            "ERROR, WARN,  false",
            "ERROR, ERROR, true"
    })
    void testLogLevelFilter(LogLevel threshold, LogLevel level, boolean expected) {
        LogLevelFilter filter = new LogLevelFilter("test", threshold);

        assertEquals(expected, filter.isLevelEnabled(level),
                () -> "isLevelEnabled failed for threshold " + threshold + " and level " + level);

        assertEquals(expected, filter.isEnabled("any.logger", level, "any.marker"),
                () -> "isEnabled failed for threshold " + threshold + " and level " + level);

        assertEquals(expected, filter.test(Instant.now(), "any.logger", level, "any.marker", null, () -> "msg", null),
                () -> "test failed for threshold " + threshold + " and level " + level);
    }
}
