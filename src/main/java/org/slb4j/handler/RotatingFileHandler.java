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

import org.jspecify.annotations.Nullable;
import org.slb4j.LocationResolver;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.support.CountingOutputStream;
import org.slb4j.support.IoStringBuilder;
import org.slb4j.support.Util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public final class RotatingFileHandler extends AbstractFileHandler {

    private final Path path;
    private final boolean append;
    private @Nullable String filePattern;

    private @Nullable Writer out;
    private final LongAdder currentSize = new LongAdder();
    private long currentEntries;
    private long nextRotationTime = -1;

    private long maxFileSize = -1;
    private long maxEntries = -1;
    private @Nullable ChronoUnit rotationTimeUnit;
    private int maxBackupIndex = 1;

    /**
     * Constructs a new FileHandler.
     *
     * @param name   the name of the handler
     * @param path   the path to the log file
     * @param append if true, then bytes will be written to the end of the file rather than the beginning
     * @throws IOException if the file cannot be opened
     */
    public RotatingFileHandler(String name, Path path, boolean append) throws IOException {
        super(name);
        this.path = path;
        this.append = append;
        openFile();
    }

    private void openFile() throws IOException {
        synchronized (lock()) {
            if (out != null) {
                out.close();
            }

            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            this.out = newWriter(append);
            updateNextRotationTime();
        }
    }

    private Writer newWriter(boolean append) throws IOException {
        synchronized (lock()) {
            StandardOpenOption[] openOptions = append ? OPTIONS_APPEND : OPTIONS_CREATE;

            if (append && Files.exists(path)) {
                currentSize.reset();
                currentSize.add(Files.size(path));
                currentEntries = countLines(path);
            } else {
                currentSize.reset();
                currentEntries = 0;
            }

            return new OutputStreamWriter(
                    new CountingOutputStream(
                            new BufferedOutputStream(Files.newOutputStream(path, openOptions)),
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
            nextRotationTime = Instant.now().truncatedTo(rotationTimeUnit).plus(1, rotationTimeUnit).toEpochMilli();
        } else {
            nextRotationTime = -1;
        }
    }

    /**
     * Sets the file pattern for archived log files.
     * The pattern can contain {@code %i} for an integer index.
     *
     * @param filePattern the file pattern
     */
    public void setFilePattern(@Nullable String filePattern) {
        synchronized (lock()) {
            this.filePattern = filePattern;
        }
    }

    /**
     * Gets the file pattern for archived log files.
     *
     * @return the file pattern
     */
    public @Nullable String getFilePattern() {
        synchronized (lock()) {
            return filePattern;
        }
    }

    /**
     * Sets the maximum file size before rotation.
     *
     * @param maxFileSize the maximum file size in bytes, or -1 for no limit
     */
    public void setMaxFileSize(long maxFileSize) {
        synchronized (lock()) {
            this.maxFileSize = maxFileSize;
        }
    }

    /**
     * Sets the maximum number of entries before rotation.
     *
     * @param maxEntries the maximum number of entries, or -1 for no limit
     */
    public void setMaxEntries(long maxEntries) {
        synchronized (lock()) {
            this.maxEntries = maxEntries;
        }
    }

    /**
     * Sets the rotation time unit.
     *
     * @param rotationTimeUnit the time unit for rotation, or null for no time-based rotation
     */
    public void setRotationTimeUnit(@Nullable ChronoUnit rotationTimeUnit) {
        synchronized (lock()) {
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
        synchronized (lock()) {
            this.maxBackupIndex = maxBackupIndex;
        }
    }

    @Override
    public void handle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, Supplier<String> msg, @Nullable Throwable t) {
        if (getFilter().test(timestamp, loggerName, lvl, mrk, mdc, msg, t)) {
            IoStringBuilder buffer = null;
            try {
                buffer = acquireBuffer();
                logPattern.formatLogEntry(buffer, timestamp, loggerName, lvl, mrk, mdc, loc, msg, t, org.slb4j.ConsoleCode.empty());
                synchronized (lock()) {
                    checkRotation(timestamp);
                    if (out != null) {
                        buffer.writeTo(out);

                        if (lvl.ordinal() >= flushLevel.ordinal()) {
                            try {
                                out.flush();
                            } catch (IOException e) {
                                Util.err().println("Error flushing log file: " + e.getMessage());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Util.err().println("Error writing log entry: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Util.err().println("Logging thread interrupted: " + e.getMessage());
            } finally {
                releaseBuffer(buffer);
            }
            currentEntries++;
        }
    }

    private void checkRotation(long timestamp) {
        boolean rotate = (maxFileSize > 0 && currentSize.longValue() >= maxFileSize)
                || (maxEntries > 0 && currentEntries >= maxEntries)
                || (nextRotationTime != -1 && timestamp >= nextRotationTime);

        if (rotate) {
            try {
                rotate();
            } catch (IOException e) {
                Util.err().println("Error during log rotation: " + e.getMessage());
            }
        }
    }

    private void rotate() throws IOException {
        synchronized (lock()) {
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
    public void close() {
        synchronized (lock()) {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    Util.err().println("Error closing log file: " + e.getMessage());
                }
                out = null;
            }
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
        synchronized (lock()) {
            return maxFileSize;
        }
    }

    /**
     * Gets the maximum number of entries before rotation.
     * @return the maximum number of entries, or -1 for no limit
     */
    public long getMaxEntries() {
        synchronized (lock()) {
            return maxEntries;
        }
    }

    /**
     * Gets the rotation time unit.
     * @return the rotation time unit, or null for no time-based rotation
     */
    public @Nullable ChronoUnit getRotationTimeUnit() {
        synchronized (lock()) {
            return rotationTimeUnit;
        }
    }

    /**
     * Gets the maximum number of backup files to keep.
     * @return the maximum number of backup files
     */
    public int getMaxBackupIndex() {
        synchronized (lock()) {
            return maxBackupIndex;
        }
    }
}
