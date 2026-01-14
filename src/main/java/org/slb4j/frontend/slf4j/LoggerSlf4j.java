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
package org.slb4j.frontend.slf4j;

import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.jspecify.annotations.Nullable;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.spi.LocationAwareLogger;

import java.io.NotSerializableException;
import java.io.Serial;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * This class represents a logger implementation using the SLF4J logging framework. It extends the AbstractLogger class.
 * <p>
 * It is used to forward SLF4J log messages to applications.
 * <p>
 * Note that SLF4J markers are not supported by this implementation, i.e., markers in logging calls are ignored and
 * logging is done as though the marker was not present.
 */
public final class LoggerSlf4j extends AbstractLogger {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final UniversalDispatcher DISPATCHER = UniversalDispatcher.getInstance();
    private static final MDC MDC_INSTANCE = new MDC() {
        @Override
        public @Nullable String get(String key) {
            return org.slf4j.MDC.get(key);
        }

        @Override
        public Stream<Map.Entry<String, String>> stream() {
            return org.slf4j.MDC.getCopyOfContextMap().entrySet().stream();
        }
    };

    /**
     * Constructs a new LoggerSlf4j instance with the specified name and handlers.
     *
     * @param name     the name of the logger
     */
    public LoggerSlf4j(String name) {
        //noinspection AssignmentToSuperclassField - API restriction; it is the only way to set the logger name
        super.name = name;
    }

    /**
     * Translates an SLF4J logging {@code Level} to the corresponding {@link LogLevel}.
     * This method maps SLF4J log levels to the application's internal {@link LogLevel} enumeration.
     *
     * @param level the SLF4J {@code Level} to translate; must not be null
     * @return the {@link LogLevel} equivalent of the provided SLF4J {@code Level}
     */
    public static LogLevel translateSlf4jLevel(Level level) {
        int levelInt = level.toInt();
        if (levelInt < LocationAwareLogger.DEBUG_INT) {
            return LogLevel.TRACE;
        }
        if (levelInt < LocationAwareLogger.INFO_INT) {
            return LogLevel.DEBUG;
        }
        if (levelInt < LocationAwareLogger.WARN_INT) {
            return LogLevel.INFO;
        }
        if (levelInt < LocationAwareLogger.ERROR_INT) {
            return LogLevel.WARN;
        }
        return LogLevel.ERROR;
    }

    @Override
    protected @Nullable String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, @Nullable Marker marker, String messagePattern, @Nullable Object @Nullable [] arguments, @Nullable Throwable throwable) {
        DISPATCHER.dispatchSlf4j(name, level, Objects.toString(marker, ""), MDC_INSTANCE, messagePattern, arguments, throwable);
    }

    /**
     * Determines if the specified logging level is enabled for this logger.
     * The method compares the logger's current level with the provided level, returning true
     * if the current level is less than or equal to the specified level.
     *
     * @param level the logging level to check
     * @return true if the provided logging level is enabled, false otherwise
     */
    public boolean isEnabled(Level level) {
        return isEnabled(null, level);
    }

    /**
     * Determines whether logging is enabled for the specified level and marker.
     * <p>
     * Note that SLF4J markers are ignored by this implementation.
     *
     * @param marker the marker to filter log messages; may be null to indicate no filtering based on markers
     * @param level  the logging level to check against
     * @return true if the current logging level is less than or equal to the specified level, false otherwise
     */
    public boolean isEnabled(@Nullable Marker marker, Level level) {
        return DISPATCHER.isEnabled(name, translateSlf4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isTraceEnabled() {
        return isEnabled(Level.TRACE);
    }

    @Override
    public boolean isTraceEnabled(@Nullable Marker marker) {
        return isEnabled(marker, Level.TRACE);
    }

    @Override
    public boolean isDebugEnabled() {
        return isEnabled(Level.DEBUG);
    }

    @Override
    public boolean isDebugEnabled(@Nullable Marker marker) {
        return isEnabled(marker, Level.DEBUG);
    }

    @Override
    public boolean isInfoEnabled() {
        return isEnabled(Level.INFO);
    }

    @Override
    public boolean isInfoEnabled(@Nullable Marker marker) {
        return isEnabled(marker, Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return isEnabled(Level.WARN);
    }

    @Override
    public boolean isWarnEnabled(@Nullable Marker marker) {
        return isEnabled(marker, Level.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return isEnabled(Level.ERROR);
    }

    @Override
    public boolean isErrorEnabled(@Nullable Marker marker) {
        return isEnabled(marker, Level.ERROR);
    }

    /**
     * Read an instance from an {@link java.io.ObjectInputStream}.
     * <p>
     * <strong>This method is unimplemented and will throw a {@link NotSerializableException}!</strong>
     *
     * @param in the ObjectInputStream instance used for reading the object during deserialization
     * @throws NotSerializableException always thrown to prevent deserialization of the object
     */
    @Serial
    private void readObject(java.io.ObjectInputStream in) throws NotSerializableException {throw new NotSerializableException("com.dua3.utility.logging.slf4j.LoggerSlf4j");}

    /**
     * Write an instance to a {@link java.io.ObjectInputStream}.
     * <p>
     * <strong>This method is unimplemented and will throw a {@link NotSerializableException}!</strong>
     *
     * @param out the ObjectOutputStream to which the object should be written
     * @throws NotSerializableException always thrown to prevent serialization of the object
     */
    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws NotSerializableException {throw new NotSerializableException("com.dua3.utility.logging.slf4j.LoggerSlf4j");}
}
