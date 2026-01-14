package org.slb4j.handler;

import org.slb4j.LogLevel;
import org.slb4j.LocationResolver;
import org.slb4j.LogPattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
            handler.setPattern(LogPattern.parse("%msg%n"));
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Hello, World!", null);
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Second line", null);
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
            handler.setPattern(LogPattern.parse("%msg%n"));
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Second line", null);
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
            handler.setPattern(LogPattern.parse("%msg%n"));
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "New content", null);
        }

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size());
        assertEquals("New content", lines.getFirst());
    }

    @Test
    void testSizeRotation() throws IOException {
        Path logFile = tempDir.resolve("test-size.log");
        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setPattern(LogPattern.parse("%msg%n"));
            handler.setMaxFileSize(10); // Very small size to trigger rotation quickly
            handler.setMaxBackupIndex(2);

            // Each log entry is "Line X\n" which is about 7 bytes.
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 1", null); // ~7 bytes
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 2", null); // ~14 bytes -> rotation should occur BEFORE or AFTER?
            // In my implementation, checkRotation is called BEFORE writing.
            // 1st entry: size 0, max 10 -> no rotate. Write "Line 1\n". currentSize = 7.
            // 2nd entry: size 7, max 10 -> no rotate. Write "Line 2\n". currentSize = 14.
            // 3rd entry: size 14, max 10 -> ROTATE. Write "Line 3\n" to NEW file.
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 3", null);
        }

        assertTrue(Files.exists(logFile));
        assertTrue(Files.exists(tempDir.resolve("test-size.log.1")));

        List<String> currentLines = Files.readAllLines(logFile);
        assertEquals(1, currentLines.size());
        assertEquals("Line 3", currentLines.getFirst());

        List<String> backupLines = Files.readAllLines(tempDir.resolve("test-size.log.1"));
        assertEquals(2, backupLines.size());
        assertEquals("Line 1", backupLines.get(0));
        assertEquals("Line 2", backupLines.get(1));
    }

    @Test
    void testEntryRotation() throws IOException {
        Path logFile = tempDir.resolve("test-entries.log");
        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setPattern(LogPattern.parse("%msg%n"));
            handler.setMaxEntries(2);
            handler.setMaxBackupIndex(2);

            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 1", null);
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 2", null);
            // 3rd entry triggers rotation because currentEntries (2) >= maxEntries (2)
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 3", null);
        }

        assertTrue(Files.exists(logFile));
        assertTrue(Files.exists(tempDir.resolve("test-entries.log.1")));

        assertEquals("Line 3", Files.readAllLines(logFile).getFirst());
        assertEquals("Line 1", Files.readAllLines(tempDir.resolve("test-entries.log.1")).get(0));
        assertEquals("Line 2", Files.readAllLines(tempDir.resolve("test-entries.log.1")).get(1));
    }

    @Test
    void testTimeRotation() throws Exception {
        Path logFile = tempDir.resolve("test-time.log");
        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setPattern(LogPattern.parse("%msg%n"));
            // Use a very small time unit if possible, but ChronoUnit.SECONDS is the smallest truncatedTo supports usually
            handler.setRotationTimeUnit(ChronoUnit.SECONDS);

            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 1", null);

            // Wait for next second
            Thread.sleep(1100);

            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 2", null);
        }

        assertTrue(Files.exists(logFile));
        assertTrue(Files.exists(tempDir.resolve("test-time.log.1")));
    }

    @Test
    void testSizeRotationWithBuffering() throws IOException {
        Path logFile = tempDir.resolve("test-size-buffered.log");
        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setPattern(LogPattern.parse("%msg")); // No newline to keep size predictable
            handler.setMaxFileSize(10);
            handler.setMaxBackupIndex(2);
            handler.setFlushEveryNEntries(1); // Ensure flush happens for this test

            // Write 5 bytes
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "12345", null);
            // currentSize should be 5 now, even if not flushed to disk yet.

            // Write 6 more bytes -> total 11, should trigger rotation on NEXT handle call if we check BEFORE write.
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "678901", null);
            // 1st entry: size 0, max 10 -> no rotate. Write "12345". currentSize = 5.
            // 2nd entry: size 5, max 10 -> no rotate. Write "678901". currentSize = 11.

            // 3rd entry: size 11, max 10 -> ROTATE.
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "ROTATE", null);
        }

        assertTrue(Files.exists(logFile));
        assertTrue(Files.exists(tempDir.resolve("test-size-buffered.log.1")));

        assertEquals("ROTATE", Files.readString(logFile));
        assertEquals("12345678901", Files.readString(tempDir.resolve("test-size-buffered.log.1")));
    }

    @Test
    void testFilePatternRotation() throws IOException {
        Path logFile = tempDir.resolve("test-pattern.log");
        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setPattern(LogPattern.parse("%msg%n"));
            handler.setFilePattern("test-archived-%i.log");
            handler.setMaxEntries(1);

            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 1", null);
            // Next one triggers rotation
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 2", null);
        }

        assertTrue(Files.exists(logFile));
        Path archived1 = tempDir.resolve("test-archived-1.log");
        assertTrue(Files.exists(archived1), "archived-1 should exist");
        assertEquals("Line 2", Files.readAllLines(logFile).getFirst());
        assertEquals("Line 1", Files.readAllLines(archived1).getFirst());

        // Test multiple rotations with pattern
        // We need to re-open the handler, and it should still find test-archived-1.log and use test-archived-2.log
        try (FileHandler handler = new FileHandler("test", logFile, true)) {
            handler.setPattern(LogPattern.parse("%msg%n"));
            handler.setFilePattern("test-archived-%i.log");
            handler.setMaxEntries(1);

            // currentEntries is 1 because logFile has "Line 2"
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "Line 3", null);
        }

        Path archived2 = tempDir.resolve("test-archived-2.log");
        assertTrue(Files.exists(archived2), "archived-2 should exist");
        assertEquals("Line 3", Files.readAllLines(logFile).getFirst());
        assertEquals("Line 2", Files.readAllLines(archived2).getFirst());
    }

    @Test
    void testFlushStrategies() throws IOException {
        Path logFile = tempDir.resolve("test-flush.log");

        // 1. Test flush on high level
        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setPattern(LogPattern.parse("%msg"));
            handler.setFlushLevel(LogLevel.ERROR);
            handler.setFlushEveryNEntries(-1); // Disable entry-based flush

            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "info", null);
            // Should be in buffer, not necessarily on disk. 
            // Files.size() might still show 0 or old size if OS/JVM hasn't flushed.
            // But wait, our check for flush is logical. 

            handler.handle(Instant.now(), "test", LogLevel.ERROR, null, null, LOC, () -> "error", null);
            // This should trigger flush.
        }
        assertEquals("infoerror", Files.readString(logFile));

        // 2. Test flush every N entries
        Files.delete(logFile);
        try (FileHandler handler = new FileHandler("test", logFile, false)) {
            handler.setPattern(LogPattern.parse("%msg"));
            handler.setFlushLevel(LogLevel.ERROR); // Only flush ERROR or higher
            handler.setFlushEveryNEntries(3);

            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "1", null);
            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "2", null);
            // No flush yet

            handler.handle(Instant.now(), "test", LogLevel.INFO, null, null, LOC, () -> "3", null);
            // This should trigger flush (3rd entry)
        }
        assertEquals("123", Files.readString(logFile));
    }
}
