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

import org.slb4j.LogLevel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


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
        LogLevelFilter filter = LogLevelFilter.pass(threshold);

        assertEquals(expected, filter.isLevelEnabled(level),
                () -> "isLevelEnabled failed for threshold " + threshold + " and level " + level);

        assertEquals(expected, filter.isEnabled("any.logger", level, "any.marker"),
                () -> "isEnabled failed for threshold " + threshold + " and level " + level);

        assertEquals(expected, filter.test(System.currentTimeMillis(), "any.logger", level, "any.marker", null, () -> "msg", null),
                () -> "test failed for threshold " + threshold + " and level " + level);
    }
}
