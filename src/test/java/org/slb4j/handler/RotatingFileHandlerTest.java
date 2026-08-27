/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slb4j.handler;

import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RotatingFileHandler ensuring compliance with Log4j2 rollover rules.
 */
class RotatingFileHandlerTest {

    private static final Location LOC = null;

    @TempDir
    Path tempDir;

    @Test
    void testBasicLogging() throws IOException {
        Path logFile = tempDir.resolve("test.log");
        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "", false, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg%n"));
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, "Hello, World!", null);
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, "Second line", null);
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

        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "", true, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg%n"));
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, "Second line", null);
        }

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size());
        assertEquals("Initial content", lines.get(0));
        assertEquals("Second line", lines.get(1));
    }

    @Test
    void testSizeRotationCheckBeforeWrite() throws IOException {
        Path logFile = tempDir.resolve("test-size.log");
        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "test-size.%i.log", false, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg")); // No newline for exact size
            handler.setMaxFileSize(10);
            handler.setMaxBackupIndex(2);

            // Entry 1: 7 bytes. Total 7.
            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "1234567", null);
            // Entry 2: 7 bytes. Total 14 > 10. ROTATE BEFORE WRITE.
            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "ABCDEFG", null);
        }

        // The file that triggered rotation (1234567) should be in .1
        assertEquals("1234567", Files.readString(tempDir.resolve("test-size.1.log")));
        // The message that caused the trigger should be the first line of the new active file
        assertEquals("ABCDEFG", Files.readString(logFile));
    }

    @Test
    void testMinIndexStrategyShifting() throws IOException {
        Path logFile = tempDir.resolve("min-test.log");
        // USE_MIN: log.1 is always the newest backup.
        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "min-test.%i.log", false, RotatingFileHandler.IndexStrategy.USE_MIN)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg"));
            handler.setMaxFileSize(5);
            handler.setMaxBackupIndex(3);

            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "AAAAA", null);
            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "BBBBB", null); // A moves to .1
            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "CCCCC", null); // B moves to .1, A moves to .2
        }

        assertEquals("CCCCC", Files.readString(logFile));
        assertEquals("BBBBB", Files.readString(tempDir.resolve("min-test.1.log")));
        assertEquals("AAAAA", Files.readString(tempDir.resolve("min-test.2.log")));
    }

    @Test
    void testMaxIndexStrategyPurge() throws IOException {
        Path logFile = tempDir.resolve("max-purge.log");
        // USE_MAX: Growing indices, deletes the lowest index when full.
        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "max-purge.%i.log", false, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg"));
            handler.setMaxFileSize(1);
            handler.setMaxBackupIndex(2);

            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "1", null); // app.log = "1"
            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "2", null); // app.log -> .1 (with "1"), app.log = "2"
            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "3", null); // app.log -> .2 (with "2"), app.log = "3"
            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "4", null); // .1 deleted, app.log -> .3 (with "3"), app.log = "4"
        }

        assertFalse(Files.exists(tempDir.resolve("max-purge.1.log")), "Oldest index (1) should be purged");
        assertTrue(Files.exists(tempDir.resolve("max-purge.2.log")));
        assertTrue(Files.exists(tempDir.resolve("max-purge.3.log")));
    }

    @Test
    void testPatternOnlyMode() throws IOException {
        // No fileName provided, only pattern. Should write directly to indices.
        String pattern = tempDir.resolve("direct-%i.log").toString();
        try (RotatingFileHandler handler = new RotatingFileHandler("test", "", pattern, false, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg"));
            handler.setMaxFileSize(5);

            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "AAAAA", null); // Writes to direct-1.log
            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "BBBBB", null); // Writes to direct-2.log
        }

        assertFalse(Files.exists(tempDir.resolve("direct.log")));
        assertEquals("AAAAA", Files.readString(tempDir.resolve("direct-1.log")));
        assertEquals("BBBBB", Files.readString(tempDir.resolve("direct-2.log")));
    }

    @Test
    void testLeadingZerosPadding() throws IOException {
        Path logFile = tempDir.resolve("padded.log");
        // %i{3} should produce 001, 002...
        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "padded-%i{3}.log", false, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg"));
            handler.setMaxFileSize(1);

            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "Data", null);
            handler.handle(0, "t", LogLevel.INFO, null, null, LOC, "Next", null);
        }

        assertTrue(Files.exists(tempDir.resolve("padded-001.log")));
    }

    @Test
    @SuppressWarnings("java:S2925")
    void testTimeRotation() throws Exception {
        Path logFile = tempDir.resolve("test-time.log");
        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "test-time.%i.log", false, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg%n"));
            handler.setRotationTimeUnit(ChronoUnit.SECONDS);

            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, "Line 1", null);
            Thread.sleep(1100);
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, "Line 2", null);
        }

        assertTrue(Files.exists(tempDir.resolve("test-time.1.log")));
    }

    @Test
    void testFlushOnLevel() throws IOException {
        Path logFile = tempDir.resolve("test-flush.log");
        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "", false, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg"));
            handler.setFlushLevel(LogLevel.ERROR);

            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, LOC, "info", null);
            // In a real OS, if we didn't flush, readString might be empty here.
            handler.handle(System.currentTimeMillis(), "test", LogLevel.ERROR, null, null, LOC, "error", null);
        }
        assertEquals("infoerror", Files.readString(logFile));
    }
}
