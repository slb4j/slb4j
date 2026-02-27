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
package org.slb4j.filter;

import org.slb4j.LogFilter;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * The LogFilter class is an implementation of the LogEntryFilter interface
 * that filters log entries based on their name and log levels defined on package/class levels.
 */
public final class LoggerNamePrefixFilter implements LogFilter {

    private final String name;
    private LogLevel level;
    private final LevelMap levelMap;

    /**
     * Constructs a LogFilter instance with the specified name.
     * The initial global log level is set to {@code LogLevel.INFO}.
     * A level map is also initialized with the global log level.
     *
     * @param name the name of the log filter
     */
    public LoggerNamePrefixFilter(String name) {
        this.name = name;
        this.level = LogLevel.TRACE;
        this.levelMap = new LevelMap(level);
    }

    private LoggerNamePrefixFilter(String name, LogLevel level, LevelMap levelMap) {
        this.name = name;
        this.level = level;
        this.levelMap = levelMap;
    }

    /**
     * Creates a deep copy of this filter.
     *
     * @return a new {@code LoggerNamePrefixFilter} instance that is a deep copy of this one.
     */
    @Override
    public LoggerNamePrefixFilter copy() {
        return new LoggerNamePrefixFilter(name, level, levelMap.copy());
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Sets the global log level of the filter.
     *
     * @param level the global log level to set
     */
    public void setLevel(LogLevel level) {
        this.level = level;
    }

    /**
     * Retrieves the global log level of the filter.
     *
     * @return The global log level of the filter.
     */
    public LogLevel getLevel() {
        return level;
    }

    /**
     * Sets the log level for a given logger name or prefix.
     *
     * @param loggerName the name or prefix of the logger(s) for which the log level is to be set
     * @param level the log level to assign
     */
    public void setLevel(String loggerName, LogLevel level) {
        levelMap.put(loggerName, level);
    }

    /**
     * Retrieves the log level associated with the specified logger name.
     *
     * @param loggerName the name of the logger whose log level is to be retrieved
     * @return the log level assigned to the specified logger.
     */
    public LogLevel getLevel(String loggerName) {
        return levelMap.level(loggerName);
    }

    @Override
    public boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, CharSequence msg, @Nullable Throwable t) {
        return (lvl.ordinal() >= level.ordinal() && lvl.ordinal() >= getLevel(loggerName).ordinal());
    }

    @Override
    public boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
        return isLevelEnabled(logLevel) && logLevel.ordinal() >= getLevel(loggerName).ordinal();
    }

    @Override
    public boolean isLevelEnabled(LogLevel logLevel) {
        return (logLevel.ordinal() >= level.ordinal());
    }

    /**
     * Retrieves the current set of log rules, where each rule associates a logger name or prefix
     * with a specific log level.
     *
     * @return a map containing logger names or prefixes as keys and their corresponding log levels as values.
     */
    public Map<String, LogLevel> getRules() {
        return levelMap.rules();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof LoggerNamePrefixFilter other)) return false;
        return level == other.level && name.equals(other.name) && levelMap.equals(other.levelMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, level, levelMap);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LogFilter[name=").append(name)
                .append(", level=").append(level)
                .append(", {");
        levelMap.getRoot().appendTo(sb);
        sb.append('}');
        return sb.toString();
    }
}
