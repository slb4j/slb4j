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
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.LogLayout;
import org.slb4j.LayoutConfigurable;
import org.slb4j.layout.PatternLayout;
import org.slb4j.support.IoStringBuilder;
import org.slb4j.support.Util;

import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public abstract sealed class AbstractFileHandler implements LogHandler, AutoCloseable, LayoutConfigurable
        permits FileHandler, RotatingFileHandler {

    private static final int BUFFER_SIZE = 8192;

    static final StandardOpenOption[] OPTIONS_APPEND = {StandardOpenOption.CREATE, StandardOpenOption.APPEND};
    static final StandardOpenOption[] OPTIONS_CREATE = {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};

    private final String name;

    /** The internal buffer. */
    protected final IoStringBuilder buffer;

    /**
     * The log pattern used by the handler to format log messages.      *
     * @see #setLayout(LogLayout) for modifying the log pattern.
     * @see #getLayout() for retrieving the current log pattern.
     */
    protected volatile LogLayout layout = PatternLayout.DEFAULT_PATTERN;
    /**
     * Represents the log filtering mechanism for the file handler.
     */
    protected volatile LogFilter filter = LogFilter.allPass();
    /**
     * The minimum log level at which a flush operation is triggered.
     * <p>
     * Defaults to {@link LogLevel#TRACE}, i.e., flushing is triggered after every log entry.
     */
    protected LogLevel flushLevel = LogLevel.TRACE;

    /**
     * Constructs a new FileHandler.
     *
     * @param name   the name of the handler
     * @throws IOException if the file cannot be opened
     */
    protected AbstractFileHandler(String name) throws IOException {
        this.name = name;
        this.buffer = new IoStringBuilder(BUFFER_SIZE);
    }

    @Override
    public final String name() {
        return name;
    }

    /**
     * Releases the specified {@code IoStringBuilder} buffer, resetting its state
     * and adding it back to the internal buffer pool for reuse.
     *
     * @param buffer the {@code IoStringBuilder} buffer to be released; can be null.
     *               If null, the method performs no operation.
     */
    protected void releaseBuffer(@Nullable IoStringBuilder buffer) {
        if (buffer != null) {
            buffer.reset(0);
        }
    }

    /**
     * Sets the log level at which a flush is triggered.
     *
     * @param flushLevel the minimum log level to trigger a flush
     */
    public void setFlushLevel(LogLevel flushLevel) {
        synchronized (buffer) {
            this.flushLevel = Objects.requireNonNull(flushLevel);
        }
    }

    @Override
    public void setFilter(LogFilter filter) {
        synchronized (buffer) {
            this.filter = Objects.requireNonNull(filter);
        }
    }

    @Override
    public LogFilter getFilter() {
        synchronized (buffer) {
            return filter;
        }
    }

    /**
     * Sets the log pattern.
     * @param layout the log pattern string
     */
    @Override
    public void setLayout(LogLayout layout) {
        synchronized (buffer) {
            this.layout = layout;
        }
    }

    /**
     * Gets the log pattern.
     * @return the log pattern string
     */
    @Override
    public LogLayout getLayout() {
        synchronized (buffer) {
            return layout;
        }
    }

    /**
     * Gets the log level at which a flush is triggered.
     * @return the minimum log level to trigger a flush
     */
    public LogLevel getFlushLevel() {
        synchronized (buffer) {
            return flushLevel;
        }
    }

    @Override
    public void shutdown() {
        synchronized (buffer) {
            try {
                close();
                buffer.reset();
            } catch (Exception e) {
                Util.err().format("Error closing handler '%s': %s", name(), e.getMessage());
                e.printStackTrace(Util.err());
            }
        }
    }
}
