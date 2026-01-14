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

import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.LogPattern;
import org.slb4j.MDC;
import org.slb4j.support.CountingOutputStream;
import org.slb4j.LocationResolver;
import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public class FileHandler implements LogHandler, AutoCloseable {

    private final String name;
    private final Path path;
    private final boolean append;
    private @Nullable String filePattern;
    private volatile LogPattern logPattern = LogPattern.DEFAULT_PATTERN;
    private volatile LogFilter filter = LogFilter.allPass();

    private @Nullable PrintStream out;
    private final LongAdder currentSize = new LongAdder();
    private long currentEntries;
    private @Nullable Instant nextRotationTime;

    private long maxFileSize = -1;
    private long maxEntries = -1;
    private @Nullable ChronoUnit rotationTimeUnit;
    private int maxBackupIndex = 1;
    private LogLevel flushLevel = LogLevel.INFO;
    private int flushEveryNEntries = 1;
    private int entriesSinceLastFlush = 0;

    /**
     * Constructs a new FileHandler.
     *
     * @param name   the name of the handler
     * @param path   the path to the log file
     * @param append if true, then bytes will be written to the end of the file rather than the beginning
     * @throws IOException if the file cannot be opened
     */
    public FileHandler(String name, Path path, boolean append) throws IOException {
        this.name = name;
        this.path = path;
        this.append = append;
        openFile();
    }

    private synchronized void openFile() throws IOException {
        if (out != null) {
            out.close();
        }

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StandardOpenOption[] options;
        if (append) {
            options = new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND};
        } else {
            options = new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        }

        this.out = newStream(options);
        updateNextRotationTime();
    }

    private synchronized PrintStream newStream(StandardOpenOption[] options) throws IOException {
        try {
            return new PrintStream(
                    new CountingOutputStream(
                            new BufferedOutputStream(Files.newOutputStream(path, options)),
                            currentSize
                    ),
                    false,
                    StandardCharsets.UTF_8
            );
        } finally {
            currentSize.reset();
            currentSize.add(Files.size(path));
            currentEntries = countLines(path);
        }
    }

    private long countLines(Path path) {
        try (var lines = Files.lines(path)) {
            return lines.count();
        } catch (IOException e) {
            return 0;
        }
    }

    private void updateNextRotationTime() {
        if (rotationTimeUnit != null) {
            nextRotationTime = Instant.now().truncatedTo(rotationTimeUnit).plus(1, rotationTimeUnit);
        } else {
            nextRotationTime = null;
        }
    }

    /**
     * Sets the file pattern for archived log files.
     * The pattern can contain {@code %i} for an integer index.
     *
     * @param filePattern the file pattern
     */
    public synchronized void setFilePattern(@Nullable String filePattern) {
        this.filePattern = filePattern;
    }

    /**
     * Gets the file pattern for archived log files.
     *
     * @return the file pattern
     */
    public synchronized @Nullable String getFilePattern() {
        return filePattern;
    }

    /**
     * Sets the maximum file size before rotation.
     *
     * @param maxFileSize the maximum file size in bytes, or -1 for no limit
     */
    public synchronized void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    /**
     * Sets the maximum number of entries before rotation.
     *
     * @param maxEntries the maximum number of entries, or -1 for no limit
     */
    public synchronized void setMaxEntries(long maxEntries) {
        this.maxEntries = maxEntries;
    }

    /**
     * Sets the rotation time unit.
     *
     * @param rotationTimeUnit the time unit for rotation, or null for no time-based rotation
     */
    public synchronized void setRotationTimeUnit(@Nullable ChronoUnit rotationTimeUnit) {
        this.rotationTimeUnit = rotationTimeUnit;
        updateNextRotationTime();
    }

    /**
     * Sets the maximum number of backup files to keep.
     *
     * @param maxBackupIndex the maximum number of backup files
     */
    public synchronized void setMaxBackupIndex(int maxBackupIndex) {
        this.maxBackupIndex = maxBackupIndex;
    }

    /**
     * Sets the log level at which a flush is triggered.
     *
     * @param flushLevel the minimum log level to trigger a flush
     */
    public synchronized void setFlushLevel(LogLevel flushLevel) {
        this.flushLevel = Objects.requireNonNull(flushLevel);
    }

    /**
     * Sets the number of entries after which a flush is triggered.
     *
     * @param flushEveryNEntries the number of entries, or -1 to disable entry-based flushing
     */
    public synchronized void setFlushEveryNEntries(int flushEveryNEntries) {
        this.flushEveryNEntries = flushEveryNEntries;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void handle(Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, Supplier<String> msg, @Nullable Throwable t) {
        if (filter.test(instant, loggerName, lvl, mrk, mdc, msg, t)) {
            synchronized (this) {
                checkRotation(instant);
                if (out != null) {
                    try {
                        logPattern.formatLogEntry(out, instant, loggerName, lvl, mrk, mdc, loc, msg, t, null);
                    } catch (IOException e) {
                        System.err.println("Error writing log entry: " + e.getMessage());
                    }
                    currentEntries++;
                    entriesSinceLastFlush++;
                    if (shouldFlush(lvl)) {
                        out.flush();
                        entriesSinceLastFlush = 0;
                    }
                }
            }
        }
    }

    private boolean shouldFlush(LogLevel lvl) {
        if (lvl.ordinal() >= flushLevel.ordinal()) {
            return true;
        }
        return flushEveryNEntries > 0 && entriesSinceLastFlush >= flushEveryNEntries;
    }

    private void checkRotation(Instant now) {
        boolean rotate = (maxFileSize > 0 && currentSize.longValue() >= maxFileSize)
                || (maxEntries > 0 && currentEntries >= maxEntries)
                || (nextRotationTime != null && !now.isBefore(nextRotationTime));

        if (rotate) {
            try {
                rotate();
            } catch (IOException e) {
                System.err.println("Error during log rotation: " + e.getMessage());
            }
        }
    }

    private synchronized void rotate() throws IOException {
        if (out != null) {
            out.close();
            out = null;
            entriesSinceLastFlush = 0;
        }

        if (filePattern != null && !filePattern.isEmpty()) {
            rotateWithPattern();
        } else {
            rotateWithIndex();
        }

        openFile();
    }

    private void rotateWithPattern() throws IOException {
        String targetName = filePattern;
        if (targetName.contains("%i")) {
            // Find the first available index
            int index = 1;
            Path targetPath;
            do {
                targetPath = path.resolveSibling(targetName.replace("%i", String.valueOf(index)));
                index++;
            } while (Files.exists(targetPath));
            Files.move(path, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
            // No index in pattern, just use it (and hope it doesn't collide or user knows what they are doing)
            // Log4J usually uses date patterns here.
            Path targetPath = path.resolveSibling(targetName);
            Files.move(path, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rotateWithIndex() throws IOException {
        // Rename existing backup files
        for (int i = maxBackupIndex - 1; i >= 1; i--) {
            Path src = getBackupPath(i);
            Path dest = getBackupPath(i + 1);
            if (Files.exists(src)) {
                Files.move(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Rename current file to .1
        if (Files.exists(path)) {
            Files.move(path, getBackupPath(1), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path getBackupPath(int index) {
        Path fileName = path.getFileName();
        assert fileName != null : "This should not have happened, path should always have a file name here - please report an issue";
        String newFileName = fileName + "." + index;
        return path.resolveSibling(newFileName);
    }

    @Override
    public synchronized void setFilter(LogFilter filter) {
        this.filter = Objects.requireNonNull(filter);
    }

    @Override
    public synchronized LogFilter getFilter() {
        return filter;
    }

    @Override
    public synchronized void close() {
        if (out != null) {
            out.close();
            out = null;
            entriesSinceLastFlush = 0;
        }
    }

    /**
     * Sets the log pattern.
     * @param logPattern the log pattern string
     */
    public synchronized void setPattern(LogPattern logPattern) {
        this.logPattern = logPattern;
    }

    /**
     * Gets the log pattern.
     * @return the log pattern string
     */
    public synchronized String getPattern() {
        return logPattern.getPattern();
    }

    /**
     * Gets the path to the log file.
     * @return the path to the log file
     */
    public Path getPath() {
        return path;
    }

    /**
     * Gets whether to append to the log file.
     * @return true if appending, false otherwise
     */
    public boolean isAppend() {
        return append;
    }

    /**
     * Gets the maximum file size before rotation.
     * @return the maximum file size in bytes, or -1 for no limit
     */
    public synchronized long getMaxFileSize() {
        return maxFileSize;
    }

    /**
     * Gets the maximum number of entries before rotation.
     * @return the maximum number of entries, or -1 for no limit
     */
    public synchronized long getMaxEntries() {
        return maxEntries;
    }

    /**
     * Gets the rotation time unit.
     * @return the rotation time unit, or null for no time-based rotation
     */
    public synchronized @Nullable ChronoUnit getRotationTimeUnit() {
        return rotationTimeUnit;
    }

    /**
     * Gets the maximum number of backup files to keep.
     * @return the maximum number of backup files
     */
    public synchronized int getMaxBackupIndex() {
        return maxBackupIndex;
    }

    /**
     * Gets the log level at which a flush is triggered.
     * @return the minimum log level to trigger a flush
     */
    public synchronized LogLevel getFlushLevel() {
        return flushLevel;
    }

    /**
     * Gets the number of entries after which a flush is triggered.
     * @return the number of entries, or -1 if entry-based flushing is disabled
     */
    public synchronized int getFlushEveryNEntries() {
        return flushEveryNEntries;
    }
}
