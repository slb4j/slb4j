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

import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.LogLayout;
import org.slb4j.LayoutConfigurable;
import org.slb4j.MDC;
import org.slb4j.layout.PatternLayout;
import org.slb4j.support.AnsiCode;
import org.jspecify.annotations.Nullable;
import org.slb4j.support.IoStringBuilder;
import org.slb4j.support.Util;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The ConsoleHandler class is an implementation of the LogEntryHandler interface.
 * It handles log entries by writing them to the console.
 */
public final class ConsoleHandler implements LogHandler, LayoutConfigurable {

    private static final int BUFFER_SIZE = 4096;

    /**
     * A default mapping of {@link LogLevel} constants to their associated {@link ConsoleCode}
     * representations for displaying log messages with ANSI color codes.
     * <p>
     * The colors follow the default Log4j color scheme.
     */
    public static final Map<LogLevel, ConsoleCode> COLOR_MAP_DEFAULT = Map.of(
            LogLevel.TRACE, ConsoleCode.ofAnsi(AnsiCode.esc(30)),  // Cyan
            LogLevel.DEBUG, ConsoleCode.ofAnsi(AnsiCode.esc(36)),  // Blue
            LogLevel.INFO, ConsoleCode.ofAnsi(AnsiCode.esc(32)),  // Green
            LogLevel.WARN, ConsoleCode.ofAnsi(AnsiCode.esc(33)),  // Yellow
            LogLevel.ERROR, ConsoleCode.ofAnsi(AnsiCode.esc(AnsiCode.BOLD_ON, 31)  // Red
            ));

    /**
     * A mapping that does not define any colors for log messages.
     * <p>
     * Use it for monochrome output.
     */
    public static final Map<LogLevel, ConsoleCode> COLOR_MAP_MONOCHROME = Map.of(
            LogLevel.TRACE, ConsoleCode.empty(),
            LogLevel.DEBUG, ConsoleCode.empty(),
            LogLevel.INFO, ConsoleCode.empty(),
            LogLevel.WARN, ConsoleCode.empty(),
            LogLevel.ERROR, ConsoleCode.empty()
    );

    private final ConsoleCode[] codesByLevelIdx = new ConsoleCode[LogLevel.values().length];

    /**
     * The default time zone used for timestamp formatting in the log messages.
     * <p>
     * The value is determined by the system's default time zone at runtime.
     */
    public static final ZoneId ZONE_ID = ZoneId.systemDefault();

    private final String name;
    private final PrintStream out;
    private final Writer writer;
    private volatile boolean colored = true;
    private volatile LogFilter filter = LogFilter.allPass();
    private volatile LogLayout layout = PatternLayout.DEFAULT_PATTERN;
    private final IoStringBuilder buffer = new IoStringBuilder(BUFFER_SIZE);

    /**
     * Set the format pattern.
     * @param layout the format pattern
     */
    @Override
    public void setLayout(LogLayout layout) {
        this.layout = layout;
    }

    /**
     * Get the format pattern.
     * @return the format pattern
     */
    @Override
    public LogLayout getLayout() {
        return layout;
    }

    /**
     * Constructs a ConsoleHandler with the specified PrintStream and colored flag.
     *
     * @param name    the name of the handler
     * @param out     the PrintStream to which log messages will be written
     * @param colored flag indicating whether to use colored brackets for different log levels
     */
    public ConsoleHandler(String name, PrintStream out, boolean colored) {
        this.name = name;
        this.out = out;
        this.writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        setColored(colored);
        String header = layout.getHeader();
        if (!header.isEmpty()) {
            out.print(header);
            out.flush();
        }
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Retrieves the PrintStream for log entries.
     * @return the PrintStream for log entries
     */
    public PrintStream getOut() {
        return out;
    }

    @Override
    public void handle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, Supplier<String> msg, @Nullable Throwable t) {
        if (filter.test(timestamp, loggerName, lvl, mrk, mdc, msg, t)) {
            ConsoleCode consoleCodes = codesByLevelIdx[lvl.ordinal()];
            String message = msg.get();
            try {
                synchronized (buffer) {
                    layout.formatLogEntry(buffer, timestamp, loggerName, lvl, mrk, mdc, loc, message, t, consoleCodes);
                    buffer.writeTo(writer);
                    buffer.reset(0);
                    writer.flush();
                }
            } catch (IOException e) {
                Util.err().println("Error writing log entry: " + e.getMessage());
            }
        }
    }

    /**
     * Enable/Disable colored output using ANSI codes.
     * @param colored true, if output use colors
     */
    public void setColored(boolean colored) {
        synchronized (buffer) {
            this.colored = colored;
            (colored ? COLOR_MAP_DEFAULT : COLOR_MAP_MONOCHROME)
                    .forEach((lvl, code) -> codesByLevelIdx[lvl.ordinal()] = code);
        }
    }

    /**
     * Check if colored output is enabled.
     * @return true, if colored output is enabled
     */
    public boolean isColored() {
        return colored;
    }

    /**
     * Sets the filter for log entries.
     *
     * @param filter the LogFilter to be set as the filter for log entries
     */
    @Override
    public void setFilter(LogFilter filter) {
        this.filter = filter;
    }

    /**
     * Retrieves the filter for log entries.
     * <p>
     * This method returns the current filter that is being used to determine if a log entry should
     * be included or excluded.
     *
     * @return the LogFilter that is currently set as the filter for log entries.
     */
    @Override
    public LogFilter getFilter() {
        return filter;
    }

    @Override
    public void shutdown() {
        synchronized (buffer) {
            String footer = layout.getFooter();
            if (!footer.isEmpty()) {
                out.print(footer);
                out.flush();
            }
            buffer.reset(0);
        }
    }
}
