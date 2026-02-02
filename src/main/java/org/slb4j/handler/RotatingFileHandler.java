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
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.support.Util;

import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public final class RotatingFileHandler extends AbstractFileHandler {

    private final Path path;
    private final boolean append;
    private @Nullable String filePattern;

    private Writer writer;
    private @Nullable FileChannel channel;

    private long nextRotationTime = -1;

    private long maxFileSize = -1;
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
        this.writer = openFile();
    }

    private Writer openFile() throws IOException {
        lock.lock();
        try {
            if (writer != null) {
                writer.close();
            }

            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            this.channel = FileChannel.open(path, append ? OPTIONS_APPEND : OPTIONS_CREATE);
            this.writer = Channels.newWriter(channel, StandardCharsets.UTF_8);

            writeLayoutHeader();
            updateNextRotationTime();

            return writer;
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
            this.filePattern = filePattern;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the file pattern for archived log files.
     *
     * @return the file pattern
     */
    public @Nullable String getFilePattern() {
        lock.lock();
        try {
            return filePattern;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the maximum file size before rotation.
     *
     * @param maxFileSize the maximum file size in bytes, or -1 for no limit
     */
    public void setMaxFileSize(long maxFileSize) {
        lock.lock();
        try {
            this.maxFileSize = maxFileSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the rotation time unit.
     *
     * @param rotationTimeUnit the time unit for rotation, or null for no time-based rotation
     */
    public void setRotationTimeUnit(@Nullable ChronoUnit rotationTimeUnit) {
        lock.lock();
        try {
            this.rotationTimeUnit = rotationTimeUnit;
            updateNextRotationTime();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the maximum number of backup files to keep.
     *
     * @param maxBackupIndex the maximum number of backup files
     */
    public void setMaxBackupIndex(int maxBackupIndex) {
        lock.lock();
        try {
            this.maxBackupIndex = maxBackupIndex;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doHandle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, String msg, @Nullable Throwable t) throws IOException {
        checkRotation(timestamp);
        super.doHandle(timestamp, loggerName, lvl, mrk, mdc, loc, msg, t);
    }

    private void checkRotation(long timestamp) throws IOException {
        boolean rotate = (channel != null && maxFileSize > 0 && channel.position() >= maxFileSize)
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
        lock.lock();
        try {
            if (writer != null) {
                writer.close();
            }

            if (filePattern != null && !filePattern.isEmpty()) {
                rotateWithPattern();
            } else {
                rotateWithIndex();
            }

            openFile();
        } finally {
            lock.unlock();
        }
    }

    private void rotateWithPattern() throws IOException {
        String targetName = Objects.requireNonNullElse(filePattern, path.getFileName()).toString();
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
    protected Writer writer() {
        return writer;
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
        lock.lock();
        try {
            return maxFileSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the rotation time unit.
     * @return the rotation time unit, or null for no time-based rotation
     */
    public @Nullable ChronoUnit getRotationTimeUnit() {
        lock.lock();
        try {
            return rotationTimeUnit;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the maximum number of backup files to keep.
     * @return the maximum number of backup files
     */
    public int getMaxBackupIndex() {
        lock.lock();
        try {
            return maxBackupIndex;
        } finally {
            lock.unlock();
        }
    }

}
