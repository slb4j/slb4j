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
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.LogLayout;
import org.slb4j.LayoutConfigurable;
import org.slb4j.MDC;
import org.slb4j.SLB4J;
import org.slb4j.layout.PatternLayout;
import org.slb4j.support.IoStringBuilder;
import org.slb4j.support.ResourcePool;

import java.io.IOException;
import java.io.Writer;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** The lock for thread-safe access. */
    protected final Object lock = new Object();

    /** The internal buffer. */
    private static final ResourcePool<IoStringBuilder> BUFFERS = ResourcePool.newThreadBasedPool(
            () -> new IoStringBuilder(BUFFER_SIZE),
            IoStringBuilder::reset
    );

    /**
     * The log pattern used by the handler to format log messages.      *
     * @see #setLayout(LogLayout) for modifying the log pattern.
     * @see #getLayout() for retrieving the current log pattern.
     */
    private LogLayout layout = PatternLayout.LAYOUT_INSTANCE_DEFAULT;

    /**
     * Represents the log filtering mechanism for the file handler.
     */
    private LogFilter filter = LogFilter.allPass();

    private static final VarHandle LAYOUT_VH;
    private static final VarHandle FILTER_VH;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            LAYOUT_VH = l.findVarHandle(AbstractFileHandler.class, "layout", LogLayout.class);
            FILTER_VH = l.findVarHandle(AbstractFileHandler.class, "filter", LogFilter.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final boolean isLocationNeeded() {
        return getLayout().isLocationNeeded();
    }

    /**
     * Releases the specified {@code IoStringBuilder} buffer, resetting its state
     * and adding it back to the internal buffer pool for reuse.
     *
     * @param buffer the {@code IoStringBuilder} buffer to be released; can be null.
     *               If null, the method performs no operation.
     */
    protected final void releaseBuffer(@Nullable IoStringBuilder buffer) {
        if (buffer != null) {
            buffer.reset(0);
        }
    }

    /**
     * Sets the log level at which a flush is triggered.
     *
     * @param flushLevel the minimum log level to trigger a flush
     */
    public final void setFlushLevel(LogLevel flushLevel) {
        synchronized (lock) {
            this.flushLevel = flushLevel;
        }
    }

    @Override
    public final void setFilter(LogFilter filter) {
        FILTER_VH.setRelease(this, filter);
    }

    @Override
    public final LogFilter getFilter() {
        return (LogFilter) FILTER_VH.getAcquire(this);
    }

    /**
     * Sets the log pattern.
     * @param layout the log pattern string
     */
    @Override
    public final void setLayout(LogLayout layout) {
        synchronized (lock) {
            LogLayout oldLayout = (LogLayout) LAYOUT_VH.getAcquire(this);
            if (oldLayout != layout) {
                Writer writer = writer();
                try {
                    String footer = oldLayout.getFooter();
                    if (!footer.isEmpty()) {
                        writer.write(footer);
                    }
                    String header = layout.getHeader();
                    if (!header.isEmpty()) {
                        writer.write(header);
                    }
                    writer.flush();
                } catch (IOException e) {
                    SLB4J.logInternal(LogLevel.WARN, "Error writing header/footer during pattern change: %s", e);
                }
                LAYOUT_VH.setRelease(this, layout);
            }
        }
    }

    /**
     * Gets the log pattern.
     * @return the log pattern string
     */
    @Override
    public final LogLayout getLayout() {
        return (LogLayout) LAYOUT_VH.getAcquire(this);
    }

    /**
     * Gets the log level at which a flush is triggered.
     * @return the minimum log level to trigger a flush
     */
    public final LogLevel getFlushLevel() {
        synchronized (lock) {
            return flushLevel;
        }
    }

    @Override
    public final void close() {
        shutdown();
    }

    @Override
    public final void handle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, CharSequence msg, @Nullable Throwable t) {
        if (getFilter().test(timestamp, loggerName, lvl, mrk, mdc, msg, t)) {
            doHandle(timestamp, loggerName, lvl, mrk, mdc, loc, msg, t);
        }
    }

    /**
     * Handles the processing and writing of a log entry.
     * <p>
     * This method formats a log entry using the specified parameters, writes the formatted
     * log entry to the associated writer, and optionally flushes the writer if the log level
     * meets or exceeds the configured flush threshold.
     *
     * @param timestamp the timestamp of the log entry in milliseconds since the epoch
     * @param loggerName the name of the logger instance
     * @param lvl the log level of the entry
     * @param mrk an optional marker associated with the log entry, or null if not applicable
     * @param mdc an optional Mapping Diagnostic Context (MDC) providing contextual data for the log entry, or null if not applicable
     * @param loc an optional location context providing code-related metadata (e.g., class name, method name, etc.), or null if not available
     * @param msg the message to be logged
     * @param t an optional throwable associated with the log entry, such as an exception, or null if not applicable
     */
    protected final void doHandle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, CharSequence msg, @Nullable Throwable t) {
        try {
            try (var lease = BUFFERS.acquire()) {
                var buffer = lease.get();
                layout.formatLogEntry(buffer, timestamp, loggerName, lvl, mrk, mdc, loc, msg, t, org.slb4j.ConsoleCode.empty());

                synchronized (lock) {
                    checkRotation(timestamp, buffer.length());
                    Writer writer = writer();
                    buffer.write(writer);

                    if (lvl.ordinal() >= flushLevel.ordinal()) {
                        writer.flush();
                    }
                }
            }
        } catch (IOException e) {
            SLB4J.logInternal(LogLevel.WARN, "Error writing log entry: %s", e);
        }
    }

    /**
     * Checks whether a log rotation should occur based on the given timestamp.
     * This method is typically overridden in subclasses to implement log rotation
     * logic, such as file rolling based on time or size constraints. If the
     * current implementation does not specify a rotation policy, this method
     * may be left empty.
     *
     * @param timestamp the timestamp of the current log event in milliseconds
     *                  since the epoch; can be used to evaluate time-based
     *                  rotation criteria
     * @param charsToWrite the number of characters to be written to the log file in the following call
     * @throws IOException if an I/O error occurs during rotation check
     */
    protected void checkRotation(long timestamp, int charsToWrite) throws IOException {
        // nothing to do
    }

    /**
     * Provides a writer instance for writing log entries.
     * Subclasses must implement this method to supply the appropriate
     * {@code Writer} for handling log outputs.
     *
     * @return a {@code Writer} instance used for writing log entries
     */
    protected abstract Writer writer();

    /**
     * Writes the layout header to the underlying writer, if it is defined and non-empty.
     * <p>
     * This method retrieves the header from the layout using {@code layout.getHeader()},
     * and writes it to the {@code Writer} instance provided by the {@code writer()} method.
     * If the header is an empty string, no action is performed. The writer is flushed
     * after the header is written to ensure the content is immediately written to the output.
     *
     * @throws IOException if an I/O error occurs while writing to the {@code Writer}
     */
    protected final void writeLayoutHeader() throws IOException {
        Writer writer = writer();
        String header = layout.getHeader();
        if (!header.isEmpty()) {
            writer.write(header);
            writer.flush();
        }
    }

    /**
     * Writes the footer section of the log layout to the underlying writer, if the
     * footer is defined and non-empty.
     * <p>
     * This method retrieves the footer from the layout using {@code layout.getFooter()}
     * and writes it to the {@code Writer} instance provided by the {@code writer()} method.
     * If the footer is an empty string or the writer is {@code null}, no action is performed.
     * After writing the footer, the writer is flushed to ensure the content is immediately
     * written to the output destination.
     *
     * @throws IOException if an I/O error occurs while writing to or flushing the {@code Writer}
     */
    protected final void writeLayoutFooter() throws IOException {
        Writer writer = writer();
        String footer = layout.getFooter();
        if (!footer.isEmpty()) {
            writer.write(footer);
            writer.flush();
        }
    }

    /**
     * Handles the shutdown process for the log handler, ensuring that any required
     * cleanup tasks are performed, such as writing the footer to the log file and
     * releasing resources.
     * <p>
     * This method writes the footer, if available, by invoking {@code layout.getFooter()}
     * and uses the {@code writer()} method to obtain a {@code Writer} instance. After writing
     * the footer, the method ensures that the writer is properly closed, even in the event
     * of an exception.
     * <p>
     * If an {@code IOException} occurs, an error message is printed to {@code Util.err()} to
     * indicate that the log file could not be closed successfully.
     * <p>
     * The method is intended to be overridden in subclasses if additional custom cleanup
     * tasks are required during shutdown.
     */
    protected final void onShutDown() {
        try {
            try (Writer writer = writer()) {
                writeLayoutFooter();
            }
        } catch (IOException e) {
            SLB4J.logInternal(LogLevel.WARN, "Error closing log file: %s", e);
        }
    }

    @Override
    public final void shutdown() {
        if (closed.compareAndSet(false, true)) {
            synchronized (lock) {
                try {
                    onShutDown();
                    writer().close();
                } catch (Exception e) {
                    SLB4J.logInternal(LogLevel.WARN, "Error shutting down handler '%s': %s", name(), e);
                }
            }
        }
    }
}
