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
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public class FileHandler implements LogHandler, AutoCloseable {

    private final String name;
    private final Object lock = new Object();
    private final Path path;
    private final boolean append;
    private @Nullable String filePattern;
    private volatile LogPattern logPattern = LogPattern.DEFAULT_PATTERN;
    private volatile LogFilter filter = LogFilter.allPass();

    private @Nullable Writer out;
    private final LongAdder currentSize = new LongAdder();
    private long currentEntries;
    private @Nullable Instant nextRotationTime;

    private long maxFileSize = -1;
    private long maxEntries = -1;
    private @Nullable ChronoUnit rotationTimeUnit;
    private int maxBackupIndex = 1;
    private LogLevel flushLevel = LogLevel.TRACE;

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

    private void openFile() throws IOException {
        synchronized (lock) {
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

            this.out = newWriter(options);
            updateNextRotationTime();
        }
    }

    private Writer newWriter(StandardOpenOption[] options) throws IOException {
        synchronized (lock) {
            if (Arrays.asList(options).contains(StandardOpenOption.APPEND) && Files.exists(path)) {
                currentSize.reset();
                currentSize.add(Files.size(path));
                currentEntries = countLines(path);
            } else {
                currentSize.reset();
                currentEntries = 0;
            }

            return new OutputStreamWriter(
                    new CountingOutputStream(
                            new BufferedOutputStream(Files.newOutputStream(path, options)),
                            currentSize
                    ),
                    StandardCharsets.UTF_8
            );
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
    public void setFilePattern(@Nullable String filePattern) {
        synchronized (lock) {
            this.filePattern = filePattern;
        }
    }

    /**
     * Gets the file pattern for archived log files.
     *
     * @return the file pattern
     */
    public @Nullable String getFilePattern() {
        synchronized (lock) {
            return filePattern;
        }
    }

    /**
     * Sets the maximum file size before rotation.
     *
     * @param maxFileSize the maximum file size in bytes, or -1 for no limit
     */
    public void setMaxFileSize(long maxFileSize) {
        synchronized (lock) {
            this.maxFileSize = maxFileSize;
        }
    }

    /**
     * Sets the maximum number of entries before rotation.
     *
     * @param maxEntries the maximum number of entries, or -1 for no limit
     */
    public void setMaxEntries(long maxEntries) {
        synchronized (lock) {
            this.maxEntries = maxEntries;
        }
    }

    /**
     * Sets the rotation time unit.
     *
     * @param rotationTimeUnit the time unit for rotation, or null for no time-based rotation
     */
    public void setRotationTimeUnit(@Nullable ChronoUnit rotationTimeUnit) {
        synchronized (lock) {
            this.rotationTimeUnit = rotationTimeUnit;
            updateNextRotationTime();
        }
    }

    /**
     * Sets the maximum number of backup files to keep.
     *
     * @param maxBackupIndex the maximum number of backup files
     */
    public void setMaxBackupIndex(int maxBackupIndex) {
        synchronized (lock) {
            this.maxBackupIndex = maxBackupIndex;
        }
    }

    /**
     * Sets the log level at which a flush is triggered.
     *
     * @param flushLevel the minimum log level to trigger a flush
     */
    public void setFlushLevel(LogLevel flushLevel) {
        synchronized (lock) {
            this.flushLevel = Objects.requireNonNull(flushLevel);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void handle(Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, Supplier<String> msg, @Nullable Throwable t) {
        if (filter.test(instant, loggerName, lvl, mrk, mdc, msg, t)) {
            synchronized (lock) {
                checkRotation(instant);
                if (out != null) {
                    try {
                        logPattern.formatLogEntry(out, instant, loggerName, lvl, mrk, mdc, loc, msg, t, null);
                    } catch (IOException e) {
                        System.err.println("Error writing log entry: " + e.getMessage());
                    }
                    currentEntries++;
                    if (lvl.ordinal() >= flushLevel.ordinal()) {
                        try {
                            out.flush();
                        } catch (IOException e) {
                            System.err.println("Error flushing log file: " + e.getMessage());
                        }
                    }
                }
            }
        }
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

    private void rotate() throws IOException {
        synchronized (lock) {
            if (out != null) {
                out.close();
                out = null;
            }

            if (filePattern != null && !filePattern.isEmpty()) {
                rotateWithPattern();
            } else {
                rotateWithIndex();
            }

            openFile();
        }
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
    public void setFilter(LogFilter filter) {
        synchronized (lock) {
            this.filter = Objects.requireNonNull(filter);
        }
    }

    @Override
    public LogFilter getFilter() {
        synchronized (lock) {
            return filter;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    System.err.println("Error closing log file: " + e.getMessage());
                }
                out = null;
            }
        }
    }

    /**
     * Sets the log pattern.
     * @param logPattern the log pattern string
     */
    public void setPattern(LogPattern logPattern) {
        synchronized (lock) {
            this.logPattern = logPattern;
        }
    }

    /**
     * Gets the log pattern.
     * @return the log pattern string
     */
    public String getPattern() {
        synchronized (lock) {
            return logPattern.getPattern();
        }
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
    public long getMaxFileSize() {
        synchronized (lock) {
            return maxFileSize;
        }
    }

    /**
     * Gets the maximum number of entries before rotation.
     * @return the maximum number of entries, or -1 for no limit
     */
    public long getMaxEntries() {
        synchronized (lock) {
            return maxEntries;
        }
    }

    /**
     * Gets the rotation time unit.
     * @return the rotation time unit, or null for no time-based rotation
     */
    public @Nullable ChronoUnit getRotationTimeUnit() {
        synchronized (lock) {
            return rotationTimeUnit;
        }
    }

    /**
     * Gets the maximum number of backup files to keep.
     * @return the maximum number of backup files
     */
    public int getMaxBackupIndex() {
        synchronized (lock) {
            return maxBackupIndex;
        }
    }

    /**
     * Gets the log level at which a flush is triggered.
     * @return the minimum log level to trigger a flush
     */
    public LogLevel getFlushLevel() {
        synchronized (lock) {
            return flushLevel;
        }
    }
}
