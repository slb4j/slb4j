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
import org.slb4j.LogPattern;
import org.slb4j.PatternConfigurable;
import org.slb4j.support.IoStringBuilder;
import org.slb4j.support.Util;

import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public abstract sealed class AbstractFileHandler implements LogHandler, AutoCloseable, PatternConfigurable
        permits FileHandler, RotatingFileHandler {

    private static final int BUFFER_COUNT = 8;
    private static final int BUFFER_SIZE = 4096;

    static final StandardOpenOption[] OPTIONS_APPEND = {StandardOpenOption.CREATE, StandardOpenOption.APPEND};
    static final StandardOpenOption[] OPTIONS_CREATE = {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};

    private final BlockingQueue<IoStringBuilder> bufferList;
    private final String name;
    private final Object lock = new Object();

    protected volatile LogPattern logPattern = LogPattern.DEFAULT_PATTERN;
    protected volatile LogFilter filter = LogFilter.allPass();
    protected LogLevel flushLevel = LogLevel.TRACE;

    /**
     * Constructs a new FileHandler.
     *
     * @param name   the name of the handler
     * @throws IOException if the file cannot be opened
     */
    protected AbstractFileHandler(String name) throws IOException {
        this.name = name;
        this.bufferList = new ArrayBlockingQueue<>(BUFFER_COUNT);
        for (int i = 0; i < BUFFER_COUNT; i++) {
            bufferList.add(new IoStringBuilder(BUFFER_SIZE));
        }
    }

    @Override
    public final String name() {
        return name;
    }

    /**
     * Provides access to the lock object used for synchronization in this handler.
     *
     * @return the lock object used for synchronizing access to critical sections of the handler
     */
    protected final Object lock() {
        return lock;
    }

    /**
     * Retrieves a reusable {@code IoStringBuilder} instance from the internal buffer pool.
     * This method blocks if no buffers are currently available, waiting until one becomes free.
     *
     * @return an {@code IoStringBuilder} instance from the buffer pool
     * @throws InterruptedException if the current thread is interrupted while waiting for a buffer
     */
    protected IoStringBuilder acquireBuffer() throws InterruptedException {
        return bufferList.take();
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
            boolean added = bufferList.offer(buffer);
            assert added : "internal error: buffer not added back to queue, this should never happen";
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

    /**
     * Sets the log pattern.
     * @param logPattern the log pattern string
     */
    @Override
    public void setPattern(LogPattern logPattern) {
        synchronized (lock) {
            this.logPattern = logPattern;
        }
    }

    /**
     * Gets the log pattern.
     * @return the log pattern string
     */
    @Override
    public LogPattern getPattern() {
        synchronized (lock) {
            return logPattern;
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

    @Override
    public void shutdown() {
        synchronized (lock()) {
            try {
                close();
                bufferList.clear();
            } catch (Exception e) {
                Util.err().format("Error closing handler '%s': %s", name(), e.getMessage());
                e.printStackTrace(Util.err());
            }
        }
    }
}
