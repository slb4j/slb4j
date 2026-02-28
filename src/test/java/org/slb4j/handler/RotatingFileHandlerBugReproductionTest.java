package org.slb4j.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slb4j.LogLevel;
import org.slb4j.layout.PatternLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduction tests for bugs identified in {@link RotatingFileHandler}.
 * These tests are designed to demonstrate issues 1.4 and 1.6 from review.md.
 */
class RotatingFileHandlerBugReproductionTest {

    @TempDir
    Path tempDir;

    /**
     * Test for Issue #9: RotatingFileHandler.rotateWithPattern logic.
     * Demonstrates that the current implementation handles maxBackupIndex correctly
     * by deleting old files when using IndexStrategy.USE_MAX.
     * Indices grow, and no renaming is performed.
     * <p>
     * Expected Result: maxBackupIndex archived files (e.g., test-archived-2.log, test-archived-3.log)
     *                  should exist in addition to the current log file.
     *                  The oldest file (test-archived-1.log) should be deleted.
     */
    @Test
    void testRespectMaxBackupIndexWithPattern() throws IOException {
        Path logFile = tempDir.resolve("test.log");
        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "test-archived-%i.log", false, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg%n"));
            handler.setMaxFileSize(1); // Force rotation on next handle() call
            handler.setMaxBackupIndex(2); // Only keep 2 archived backups

            // 1st log: writes to test.log. size becomes > 1.
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, "Line 1", null);

            // 2nd log: triggers rotation. test.log -> test-archived-1.log. Writes "Line 2" to new test.log.
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, "Line 2", null);

            // 3rd log: triggers rotation. test.log -> test-archived-2.log. Writes "Line 3" to new test.log.
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, "Line 3", null);

            // 4th log: triggers rotation.
            // It should delete test-archived-1.log.
            // It should roll test.log to test-archived-3.log.
            // Writes "Line 4" to new test.log.
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, "Line 4", null);
        }

        // We should only have test.log, test-archived-2.log, test-archived-3.log
        assertTrue(Files.exists(tempDir.resolve("test.log")));
        assertFalse(Files.exists(tempDir.resolve("test-archived-1.log")), "Should have deleted oldest backup");
        assertTrue(Files.exists(tempDir.resolve("test-archived-2.log")));
        assertTrue(Files.exists(tempDir.resolve("test-archived-3.log")), "Indices should grow without renaming");
    }

    /**
     * Test for issue #10: Tests the edge case where maxBackupIndex is set to 0.
     * Rotating should delete the old file and start fresh without creating any backups.
     * <p>
     * Expected Result: test-zero.log exists with the latest message; no backup files (.log.1) exist.
     * Actual Result: Depends on implementation, but typically the first backup might still be created.
     */
    @Test
    void testMaxBackupIndexZero() throws IOException {
        Path logFile = tempDir.resolve("test-zero.log");
        try (RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "", false, RotatingFileHandler.IndexStrategy.USE_MAX)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%msg%n"));
            handler.setMaxFileSize(1);
            handler.setMaxBackupIndex(0);

            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, "Line 1", null);
            handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, "Line 2", null);
        }

        assertTrue(Files.exists(logFile));
        assertEquals(1, Files.readAllLines(logFile).size());
        assertEquals("Line 2", Files.readAllLines(logFile).getFirst());

        // There should be no backup files
        assertFalse(Files.exists(tempDir.resolve("test-zero.log.1")), "Should have no backup with maxBackupIndex=0");
    }

    /**
     * Test for Issue: RotatingFileHandler Broken State on Rotation Failure.
     * Demonstrates that if a rotation fails (e.g., due to an invalid file pattern), the
     * internal writer is closed but openFile() is not called in a way that recovers the handler.
     * Subsequent log attempts fail because the writer/channel is closed.
     * <p>
     * Expected Result: The handler should remain usable even after a failed rotation attempt.
     * Actual Result (Bug): Subsequent logs fail with ClosedChannelException (swallowed and logged to status logger).
     */
    @Test
    void testHandlerUsableAfterRotationFailure() throws IOException {
        Path logFile = tempDir.resolve("test-fail.log");
        Files.writeString(logFile, "Pre-existing content\n");

        RotatingFileHandler handler = new RotatingFileHandler("test", logFile.toString(), "/non-existent-directory/archived.log", true, RotatingFileHandler.IndexStrategy.USE_MAX);
        // Set a pattern that will definitely fail on move (e.g. into a non-existent directory)
        handler.setLayout(PatternLayout.parseLog4jPattern("%msg%n"));
        handler.setMaxFileSize(1); // Force rotation on next write

        // This call triggers rotate(). rotate() calls writer.close(), then rotateWithPattern() which throws IOException.
        // nextFile() is never called, or if it was, rotate() has already exited exceptionally.
        // The IOException is caught by AbstractFileHandler.doHandle and logged to status logger.
        handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, "Should trigger fail", null);

        // Now check if we can still log.
        // If it's broken, this will fail to write to the file.
        handler.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, "Next line", null);
        handler.close();

        assertTrue(Files.exists(logFile));
        List<String> lines = Files.readAllLines(logFile);
        
        // This assertion fails because "Next line" is never written to the closed channel.
        assertTrue(lines.contains("Next line"), "Handler should still be usable after rotation failure");
    }
}
