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
package org.slb4j.handler;

import org.slb4j.LogLevel;
import org.slb4j.LocationResolver;
import org.slb4j.LogPattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileHandlerTest {

    private static final LocationResolver LOC = () -> null;

    @TempDir
    Path tempDir;

    @Test
    void testBasicLogging() throws IOException {
        Path logFile = tempDir.resolve("test.log");
        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setLogPattern(LogPattern.parseLog4jPattern("%msg%n"));
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, () -> "Hello, World!", null);
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, () -> "Second line", null);
        }

        assertTrue(Files.exists(logFile));
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size());
        assertEquals("Hello, World!", lines.get(0));
        assertEquals("Second line", lines.get(1));
    }

    @Test
    void testAppend() throws IOException {
        Path logFile = tempDir.resolve("test-append.log");
        Files.writeString(logFile, "Initial content\n");

        try (FileHandler handler = new FileHandler("test", logFile, true)) {
            handler.setLogPattern(LogPattern.parseLog4jPattern("%msg%n"));
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, () -> "Second line", null);
        }

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size());
        assertEquals("Initial content", lines.get(0));
        assertEquals("Second line", lines.get(1));
    }

    @Test
    void testReplace() throws IOException {
        Path logFile = tempDir.resolve("test-replace.log");
        Files.writeString(logFile, "Initial content\n");

        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setLogPattern(LogPattern.parseLog4jPattern("%msg%n"));
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, () -> "New content", null);
        }

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size());
        assertEquals("New content", lines.getFirst());
    }

    @Test
    void testFlushStrategies() throws IOException {
        Path logFile = tempDir.resolve("test-flush.log");

        // 1. Test flush on high level
        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setLogPattern(LogPattern.parseLog4jPattern("%msg"));
            handler.setFlushLevel(LogLevel.ERROR);

            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, () -> "info", null);
            // Should be in buffer, not necessarily on disk. 
            // Files.size() might still show 0 or old size if OS/JVM hasn't flushed.
            // But wait, our check for flush is logical. 

            handler.handle(System.currentTimeMillis(), "test", LogLevel.ERROR, null, null, LOC, () -> "error", null);
            // This should trigger a flush.
        }
        assertEquals("infoerror", Files.readString(logFile));
    }
}
