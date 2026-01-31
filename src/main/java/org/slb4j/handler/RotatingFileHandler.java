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
import org.slb4j.LogLayout;
import org.slb4j.MDC;
import org.slb4j.support.IoStringBuilder;
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
        openFile();
    }

    private void openFile() throws IOException {
        synchronized (buffer) {
            if (out != null) {
                out.close();
            }

            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            this.channel = FileChannel.open(path, append ? OPTIONS_APPEND : OPTIONS_CREATE);
            this.out = Channels.newWriter(channel, StandardCharsets.UTF_8);
            String header = layout.getHeader();
            if (!header.isEmpty()) {
                out.write(header);
                out.flush();
            }

            updateNextRotationTime();
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
        synchronized (buffer) {
            this.filePattern = filePattern;
        }
    }

    /**
     * Gets the file pattern for archived log files.
     *
     * @return the file pattern
     */
    public @Nullable String getFilePattern() {
        synchronized (buffer) {
            return filePattern;
        }
    }

    /**
     * Sets the maximum file size before rotation.
     *
     * @param maxFileSize the maximum file size in bytes, or -1 for no limit
     */
    public void setMaxFileSize(long maxFileSize) {
        synchronized (buffer) {
            this.maxFileSize = maxFileSize;
        }
    }

    /**
     * Sets the rotation time unit.
     *
     * @param rotationTimeUnit the time unit for rotation, or null for no time-based rotation
     */
    public void setRotationTimeUnit(@Nullable ChronoUnit rotationTimeUnit) {
        synchronized (buffer) {
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
        synchronized (buffer) {
            this.maxBackupIndex = maxBackupIndex;
        }
    }

    @Override
    public void handle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, Supplier<String> msg, @Nullable Throwable t) {
        if (getFilter().test(timestamp, loggerName, lvl, mrk, mdc, msg, t)) {
            String message = msg.get();
            try {
                synchronized (buffer) {
                    layout.formatLogEntry(buffer, timestamp, loggerName, lvl, mrk, mdc, loc, message, t, org.slb4j.ConsoleCode.empty());
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
            } finally {
                releaseBuffer(buffer);
            }
        }
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
        synchronized (buffer) {
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
    public void close() {
        synchronized (buffer) {
            if (out != null) {
                try {
                    String footer = layout.getFooter();
                    if (!footer.isEmpty()) {
                        out.write(footer);
                        out.flush();
                    }
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
        synchronized (buffer) {
            return maxFileSize;
        }
    }

    /**
     * Gets the rotation time unit.
     * @return the rotation time unit, or null for no time-based rotation
     */
    public @Nullable ChronoUnit getRotationTimeUnit() {
        synchronized (buffer) {
            return rotationTimeUnit;
        }
    }

    /**
     * Gets the maximum number of backup files to keep.
     * @return the maximum number of backup files
     */
    public int getMaxBackupIndex() {
        synchronized (buffer) {
            return maxBackupIndex;
        }
    }

    @Override
    public void setLayout(LogLayout layout) {
        synchronized (buffer) {
            if (this.layout != layout) {
                if (out != null) {
                    try {
                        String footer = this.layout.getFooter();
                        if (!footer.isEmpty()) {
                            out.write(footer);
                        }
                        String header = layout.getHeader();
                        if (!header.isEmpty()) {
                            out.write(header);
                        }
                        out.flush();
                    } catch (IOException e) {
                        Util.err().println("Error writing header/footer during pattern change: " + e.getMessage());
                    }
                }
                this.layout = layout;
            }
        }
    }
}
