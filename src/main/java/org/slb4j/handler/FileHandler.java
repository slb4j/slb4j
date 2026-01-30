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

import org.slb4j.LogPattern;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.LocationResolver;
import org.jspecify.annotations.Nullable;
import org.slb4j.support.IoStringBuilder;
import org.slb4j.support.Util;

import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public final class FileHandler extends AbstractFileHandler {

    private final Path path;
    private final boolean append;
    private final FileChannel channel;
    private final Writer writer;

    /**
     * Constructs a new FileHandler.
     *
     * @param name   the name of the handler
     * @param path   the path to the log file
     * @param append if true, then bytes will be written to the end of the file rather than the beginning
     * @throws IOException if the file cannot be opened
     */
    public FileHandler(String name, Path path, boolean append) throws IOException {
        super(name);
        this.path = path;
        this.append = append;
        this.channel = FileChannel.open(path, append ? OPTIONS_APPEND : OPTIONS_CREATE);
        this.writer = Channels.newWriter(channel, StandardCharsets.UTF_8);
        String header = logPattern.getHeader();
        if (!header.isEmpty()) {
            writer.write(header);
            writer.flush();
        }
    }

    @Override
    public void handle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, Supplier<String> msg, @Nullable Throwable t) {
        if (filter.test(timestamp, loggerName, lvl, mrk, mdc, msg, t)) {
            IoStringBuilder buffer = null;
            String message = msg.get();
            try {
                buffer = acquireBuffer();
                logPattern.formatLogEntry(buffer, timestamp, loggerName, lvl, mrk, mdc, loc, message, t, org.slb4j.ConsoleCode.empty());
                synchronized (lock()) {
                    buffer.writeTo(writer);

                    if (lvl.ordinal() >= flushLevel.ordinal()) {
                        try {
                            writer.flush();
                        } catch (IOException e) {
                            Util.err().println("Error flushing log file: " + e.getMessage());
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
        }
    }

    @Override
    public void close() {
        synchronized (lock()) {
            try {
                String footer = logPattern.getFooter();
                if (!footer.isEmpty()) {
                    writer.write(footer);
                    writer.flush();
                }
                writer.close();
            } catch (IOException e) {
                Util.err().println("Error closing log file: " + e.getMessage());
            }
        }
    }

    @Override
    public void setLogPattern(LogPattern logPattern) {
        synchronized (lock()) {
            if (this.logPattern != logPattern) {
                try {
                    String footer = this.logPattern.getFooter();
                    if (!footer.isEmpty()) {
                        writer.write(footer);
                    }
                    String header = logPattern.getHeader();
                    if (!header.isEmpty()) {
                        writer.write(header);
                    }
                    writer.flush();
                } catch (IOException e) {
                    Util.err().println("Error writing header/footer during pattern change: " + e.getMessage());
                }
                this.logPattern = logPattern;
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
}
