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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class SupportTest {

    @Test
    void testUtilCachingStringSupplier() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> original = () -> {
            counter.incrementAndGet();
            return "hello";
        };

        Supplier<String> cached = Util.cachingStringSupplier(original);

        assertEquals(0, counter.get());
        assertEquals("hello", cached.get());
        assertEquals(1, counter.get());
        assertEquals("hello", cached.get());
        assertEquals(1, counter.get());

        // Test that it doesn't wrap twice
        assertSame(cached, Util.cachingStringSupplier(cached));
    }

    @Test
    void testSharableAndSharedString() {
        String base = "hello world";
        SharableString ss = new SharableString(base);

        assertEquals(base.length(), ss.length());
        assertEquals('h', ss.charAt(0));
        assertEquals(base, ss.toString());
        assertEquals(base.hashCode(), ss.hashCode());
        assertEquals(new SharableString(base), ss);

        SharedString sub = ss.subSequence(6, 11);
        assertEquals("world", sub.toString());
        assertEquals(5, sub.length());
        assertEquals('w', sub.charAt(0));

        SharedString subSub = sub.subSequence(1, 4);
        assertEquals("orl", subSub.toString());

        // Test equality and hashcode
        SharedString anotherWorld = new SharableString("other world").subSequence(6, 11);
        assertEquals("world", anotherWorld.toString());
        assertEquals(sub, anotherWorld);
        assertEquals(sub.hashCode(), anotherWorld.hashCode());
    }

    @Test
    void testSharableStringIndexOf() {
        SharableString ss = new SharableString("hello world");
        assertEquals(2, ss.indexOf('l', 0));
        assertEquals(3, ss.indexOf('l', 3));
        assertEquals(9, ss.indexOf('l', 4));
        assertEquals(-1, ss.indexOf('z', 0));
    }

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
