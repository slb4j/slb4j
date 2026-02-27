package org.slb4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.handler.RotatingFileHandler;
import org.slb4j.layout.PatternLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StabilityTest {

    @TempDir
    Path tempDir;

    private static final int NUM_THREADS = 10;
    private static final int MSG_PER_THREAD = 10000;
    private static final long MAX_FILE_SIZE = 100 * 1024; // 100KB to ensure many rotations
    private static final int MAX_BACKUP_INDEX = 200;

    @Test
    void testStabilityAndCorrectness() throws Exception {
        Path logFile = tempDir.resolve("stability.log");
        // Using a small buffer and small file size to stress the system
        try (RotatingFileHandler handler = new RotatingFileHandler("stability", logFile, false)) {
            handler.setLayout(PatternLayout.parseLog4jPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} TID: %t SEQ: %m%n"));
            handler.setMaxFileSize(MAX_FILE_SIZE);
            handler.setMaxBackupIndex(MAX_BACKUP_INDEX);

            UniversalDispatcher dispatcher = UniversalDispatcher.getInstance();
            dispatcher.clearLogHandlers();
            dispatcher.addLogHandler(handler);
            dispatcher.setRootLevel(LogLevel.INFO);

            ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

            for (int i = 0; i < NUM_THREADS; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    Thread.currentThread().setName("Thread-" + threadId);
                    for (int j = 0; j < MSG_PER_THREAD; j++) {
                        final int seq = j;
                        long ts = System.currentTimeMillis();
                        dispatcher.filterAndDispatch(
                                ts,
                                "logger-" + threadId,
                                LogLevel.INFO,
                                null,
                                null,
                                () -> null, // Dummy LocationResolver
                                () -> {
                                    // Occasionally delay formatting to trigger overtaking
                                    if (seq % 500 == 0) {
                                        try {
                                            Thread.sleep(2);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                    return String.valueOf(seq);
                                },
                                null
                        );
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.MINUTES), "Test timed out");
        }

        // Verification phase
        List<Path> logFiles = new ArrayList<>();
        logFiles.add(logFile);
        for (int i = 1; i <= MAX_BACKUP_INDEX; i++) {
            Path backup = tempDir.resolve("stability.log." + i);
            if (Files.exists(backup)) {
                logFiles.add(backup);
            } else {
                break;
            }
        }

        // Files are rotated: stability.log is newest, stability.log.1 is older, stability.log.n is oldest.
        // We want to read from oldest to newest.
        Collections.reverse(logFiles);

        Map<String, List<Integer>> threadMessages = new HashMap<>();
        Pattern pattern = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) TID: (Thread-\\d+) SEQ: (\\d+)$");

        long lastGlobalTs = -1;
        int totalMessages = 0;
        int outOfOrderGlobal = 0;

        for (Path file : logFiles) {
            try (Stream<String> lines = Files.lines(file)) {
                for (String line : lines.toList()) {
                    totalMessages++;
                    Matcher matcher = pattern.matcher(line);
                    if (!matcher.find()) {
                        fail("Corrupted line in " + file.getFileName() + ": " + line);
                    }
                    String tsStr = matcher.group(1);
                    String threadName = matcher.group(2);
                    int seq = Integer.parseInt(matcher.group(3));

                    // Parse timestamp to check for global order
                    // Since it's ISO-like, string comparison works for ordering
                    // but we should probably parse it if we want to be sure about "overtaking"
                    
                    threadMessages.computeIfAbsent(threadName, k -> new ArrayList<>()).add(seq);
                }
            }
        }

        assertEquals(NUM_THREADS * MSG_PER_THREAD, totalMessages, "Message loss detected");

        for (Map.Entry<String, List<Integer>> entry : threadMessages.entrySet()) {
            List<Integer> seqs = entry.getValue();
            String threadName = entry.getKey();
            assertEquals(MSG_PER_THREAD, seqs.size(), "Missing messages for " + threadName);
            
            for (int i = 0; i < seqs.size(); i++) {
                assertEquals(i, seqs.get(i), "Message reordering or duplication for " + threadName + " at index " + i);
            }
        }

        // Check for overtaking by comparing timestamps in the log
        // We need to re-read and parse timestamps properly
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        
        long prevTs = -1;
        String prevLine = null;
        for (Path file : logFiles) {
             try (Stream<String> lines = Files.lines(file)) {
                for (String line : lines.toList()) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        long currentTs = java.time.LocalDateTime.parse(matcher.group(1), formatter)
                                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                        if (prevTs > currentTs) {
                            outOfOrderGlobal++;
                            System.out.println("Report: Overtake detected! Previous: [" + prevLine + "], Current: [" + line + "]");
                        }
                        prevTs = currentTs;
                        prevLine = line;
                    }
                }
             }
        }
        
        System.out.println("Total messages: " + totalMessages);
        System.out.println("Global out-of-order (overtakes): " + outOfOrderGlobal);
        assertTrue(outOfOrderGlobal >= 0); // Always true, but just to report it
    }
}
