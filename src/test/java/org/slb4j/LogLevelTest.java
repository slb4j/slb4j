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
package org.slb4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogLevelTest {

    @ParameterizedTest
    @EnumSource(LogLevel.class)
    void testLogLevelPresence(LogLevel level) {
        assertNotNull(level);
    }

    @Test
    void testLogLevelOrder() {
        assertTrue(LogLevel.TRACE.ordinal() < LogLevel.DEBUG.ordinal());
        assertTrue(LogLevel.DEBUG.ordinal() < LogLevel.INFO.ordinal());
        assertTrue(LogLevel.INFO.ordinal() < LogLevel.WARN.ordinal());
        assertTrue(LogLevel.WARN.ordinal() < LogLevel.ERROR.ordinal());
    }

    @Test
    void testConsoleCode() {
        ConsoleCode cc = ConsoleCode.of("\033[1m", "\033[0m");
        assertEquals("\033[1m", cc.start());
        assertEquals("\033[0m", cc.end());

        ConsoleCode ansi = ConsoleCode.ofAnsi("\033[3m");
        assertEquals("\033[3m", ansi.start());
        assertEquals("\033[0m", ansi.end());

        ConsoleCode empty = ConsoleCode.empty();
        assertEquals("", empty.start());
        assertEquals("", empty.end());
    }
}
