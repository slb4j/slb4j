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
import org.slb4j.LogFilter;
import org.slb4j.LogPattern;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.support.AnsiCode;
import org.slb4j.LocationResolver;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The ConsoleHandler class is an implementation of the LogEntryHandler interface.
 * It handles log entries by writing them to the console.
 */
public final class ConsoleHandler implements LogHandler {

    private static final Map<LogLevel, ConsoleCode> COLOR_MAP_COLORED = Map.of(
            LogLevel.TRACE, ConsoleCode.ofAnsi(AnsiCode.italic(true)),
            LogLevel.DEBUG, ConsoleCode.empty(),
            LogLevel.INFO, ConsoleCode.of(AnsiCode.bold(true), ""),
            LogLevel.WARN, ConsoleCode.ofAnsi(AnsiCode.fg(0xFF, 0x45, 0x00) + AnsiCode.bold(true)),
            LogLevel.ERROR, ConsoleCode.ofAnsi(AnsiCode.fg(0x8B, 0x00, 0x00) + AnsiCode.bold(true))
    );

    private static final Map<LogLevel, ConsoleCode> COLOR_MAP_MONOCHROME = Map.of(
            LogLevel.TRACE, ConsoleCode.empty(),
            LogLevel.DEBUG, ConsoleCode.empty(),
            LogLevel.INFO, ConsoleCode.empty(),
            LogLevel.WARN, ConsoleCode.empty(),
            LogLevel.ERROR, ConsoleCode.empty()
    );

    /**
     * The default time zone used for timestamp formatting in the log messages.
     * <p>
     * The value is determined by the system's default time zone at runtime.
     */
    public static final ZoneId ZONE_ID = ZoneId.systemDefault();

    private final String name;
    private final PrintStream out;
    private volatile LogFilter filter = LogFilter.allPass();
    private volatile Map<LogLevel, ConsoleCode> colorMap = new EnumMap<>(LogLevel.class);
    private volatile LogPattern logPattern = LogPattern.DEFAULT_PATTERN;

    /**
     * Set the format pattern.
     * @param logPattern the format pattern
     */
    public void setPattern(LogPattern logPattern) {
        this.logPattern = logPattern;
    }

    /**
     * Get the format pattern.
     * @return the format pattern
     */
    public LogPattern getPattern() {
        return logPattern;
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
        setColored(colored);
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
    public void handle(Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, Supplier<String> msg, @Nullable Throwable t) {
        if (filter.test(instant, loggerName, lvl, mrk, mdc, msg, t)) {
            ConsoleCode consoleCodes = colorMap.get(lvl);
            try {
                logPattern.formatLogEntry(out, instant, loggerName, lvl, mrk, mdc, loc, msg, t, consoleCodes);
            } catch (IOException e) {
                System.err.println("Error writing log entry: " + e.getMessage());
            }
        }
    }

    /**
     * Enable/Disable colored output using ANSI codes.
     * @param colored true, if output use colors
     */
    public void setColored(boolean colored) {
        colorMap = colored ? COLOR_MAP_COLORED : COLOR_MAP_MONOCHROME;
    }

    /**
     * Check if colored output is enabled.
     * @return true, if colored output is enabled
     */
    public boolean isColored() {
        return colorMap == COLOR_MAP_COLORED;
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
}
