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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupportTest {

    @Test
    void testAnsiCode() {
        assertEquals("\033[m", AnsiCode.reset());
        assertEquals("\033[1m", AnsiCode.bold(true));
        assertEquals("\033[22m", AnsiCode.bold(false));
        assertEquals("\033[38;2;255;0;0m", AnsiCode.fg(255, 0, 0));
        assertEquals("\033[48;2;0;255;0m", AnsiCode.bg(0, 255, 0));
    }

    @Test
    void testPathToNormalizedString() {
        assertEquals("a/b/c", Util.pathToNormalizedString(java.nio.file.Paths.get("a", "b", "c")));
        assertEquals("a/b/c", Util.pathToNormalizedString(java.nio.file.Paths.get("a/b/c")));
        if (java.io.File.separatorChar == '\\') {
            assertEquals("C:/a/b/c", Util.pathToNormalizedString(java.nio.file.Paths.get("C:\\a\\b\\c")));
        }
    }
}
