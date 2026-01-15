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

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public abstract class AbstractFileHandler implements LogHandler, AutoCloseable {

    private final String name;
    private final Object lock = new Object();

    protected volatile LogPattern logPattern = LogPattern.DEFAULT_PATTERN;
    protected volatile LogFilter filter = LogFilter.allPass();
    protected LogLevel flushLevel = LogLevel.TRACE;

    /**
     * Constructs a new FileHandler.
     *
     * @param name   the name of the handler
     * @param path   the path to the log file
     * @param append if true, then bytes will be written to the end of the file rather than the beginning
     * @throws IOException if the file cannot be opened
     */
    public AbstractFileHandler(String name) throws IOException {
        this.name = name;
    }

    @Override
    public final String name() {
        return name;
    }

    protected final Object lock() {
        return lock;
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
    public void setPattern(LogPattern logPattern) {
        synchronized (lock) {
            this.logPattern = logPattern;
        }
    }

    /**
     * Gets the log pattern.
     * @return the log pattern string
     */
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
}
