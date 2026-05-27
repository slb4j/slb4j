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
import java.util.stream.Collectors;

/**
 * The LogFilter class is an implementation of the LogEntryFilter interface
 * that filters log entries based on their name and log levels defined on package/class levels.
 */
public final class LoggerNamePrefixFilter implements LogFilter {

    private final String name;
    private final LevelMap levelMap;

    /**
     * Constructs a LogFilter instance with the specified name.
     * <p>
     * The root node is initialized to {@code LogLevel.ERROR}.
     * A level map is also initialized with the global log level.
     *
     * @param name the name of the log filter
     */
    public LoggerNamePrefixFilter(String name) {
        this.name = name;
        this.levelMap = new LevelMap(LogLevel.ERROR.ordinal());
    }

    private LoggerNamePrefixFilter(String name, LevelMap levelMap) {
        this.name = name;
        this.levelMap = levelMap;
    }

    /**
     * Creates a deep copy of this filter.
     *
     * @return a new {@code LoggerNamePrefixFilter} instance that is a deep copy of this one.
     */
    @Override
    public LoggerNamePrefixFilter copy() {
        return new LoggerNamePrefixFilter(name, levelMap.copy());
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Sets the log level of the root node.
     *
     * @param level the level to set
     */
    public void setRootLevel(LogLevel level) {
        levelMap.setRootLevel(level.ordinal());
    }

    /**
     * Retrieves the level configured for the root node.
     *
     * @return The level configured for the root node.
     */
    public LogLevel getRootLevel() {
        return toLogLevel(levelMap.getRootLevel());
    }

    private static final LogLevel toLogLevel(int level) {
        return LogLevel.values()[level];
    }

    private static final @Nullable LogLevel toLogLevelOrNull(int level) {
        return level < 0 ? null : LogLevel.values()[level];
    }

    /**
     * Sets the log level for a given logger name or prefix.
     *
     * @param loggerName the name or prefix of the logger(s) for which the log level is to be set
     * @param level the log level to assign
     */
    public void setLevel(String loggerName, LogLevel level) {
        levelMap.put(loggerName, level.ordinal());
    }

    /**
     * Retrieves the effective log level for the specified logger name.
     *
     * @param loggerName the name of the logger whose log level is to be retrieved
     * @return the effective log level for the specified logger.
     */
    public LogLevel getLevel(String loggerName) {
        return toLogLevel(levelMap.level(loggerName));
    }

    @Override
    public boolean test(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, CharSequence msg, @Nullable Throwable t) {
        return levelMap.isEnabled(loggerName, lvl.ordinal());
    }

    @Override
    public boolean isEnabled(String loggerName, LogLevel logLevel, @Nullable String marker) {
        return levelMap.isEnabled(loggerName, logLevel.ordinal());
    }

    @Override
    public boolean isLevelEnabled(LogLevel logLevel) {
        return (logLevel.ordinal() >= levelMap.getMinLevel());
    }

    /**
     * Retrieves the current set of log rules, where each rule associates a logger name or prefix
     * with a specific log level.
     *
     * @return a map containing logger names or prefixes as keys and their corresponding log levels as values.
     */
    public Map<String, LogLevel> getRules() {
        return levelMap.rules().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toLogLevelOrNull(entry.getValue())
                ));
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof LoggerNamePrefixFilter other)) return false;
        return name.equals(other.name) && levelMap.equals(other.levelMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, levelMap);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LogFilter[name=").append(name)
                .append(", {");
        levelMap.getRoot().appendTo(sb);
        sb.append('}');
        return sb.toString();
    }
}
