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
package org.slb4j.frontend.log4j;

import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ReusableMessageFactory;
import org.slb4j.LocationResolver;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.SLB4J;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.spi.StandardLevel;
import org.jspecify.annotations.Nullable;
import org.slb4j.support.ResourcePool;
import org.slb4j.support.StackWalkerLocationResolver;

import java.util.Map;

/**
 * LoggerLog4j is an implementation of the Log4J AbstractLogger class that forwards all logging
 * calls to the global UniversalDispatcher instance.
 */
public final class LoggerLog4j extends AbstractLogger {
    private static final UniversalDispatcher DISPATCHER = UniversalDispatcher.getInstance();
    private static final LocationResolver LOCATION_RESOLVER = new StackWalkerLocationResolver(LoggerLog4j.class, "org.apache.logging");

    /**
     * A pool of formatters to use for formatting log messages.
     */
    private static final ResourcePool<Log4JMessageFormatter> FORMATTERS = ResourcePool.newThreadBasedResourcePool(Log4JMessageFormatter::new, Log4JMessageFormatter::cleanup);

    private static final MDC MDC_INSTANCE = new MDC() {
        @Override
        public @Nullable String get(String key) {
            return ThreadContext.get(key);
        }

        @Override
        public Map<String, String> get() {
            return ThreadContext.getImmutableContext();
        }
    };

    /**
     * Constructs a new LoggerLog4j instance with the specified logger name.
     *
     * @param name the name of the logger to be associated with this instance
     */
    public LoggerLog4j(String name) {
        this(name, ReusableMessageFactory.INSTANCE);
    }

    /**
     * Constructs a new LoggerLog4j instance with the specified logger name and message factory.
     *
     * @param name the name of the logger to be associated with this instance
     * @param messageFactory the message factory to be used by this logger
     */
    public LoggerLog4j(String name, MessageFactory messageFactory) {
        super(name, messageFactory);
    }

    /**
     * Translates a Log4j {@code Level} into the corresponding {@link LogLevel}.
     * This method maps Log4j log levels to the application's internal {@link LogLevel} enumeration
     * based on the relative severity of the levels.
     *
     * @param level the Log4j {@code Level} to be translated; must not be null
     * @return the {@link LogLevel} equivalent of the provided Log4j {@code Level}
     */
    public static LogLevel translateLog4jLevel(Level level) {
        int levelInt = level.intLevel();
        if (levelInt > StandardLevel.DEBUG.intLevel()) {
            return LogLevel.TRACE;
        }
        if (levelInt > StandardLevel.INFO.intLevel()) {
            return LogLevel.DEBUG;
        }
        if (levelInt > StandardLevel.WARN.intLevel()) {
            return LogLevel.INFO;
        }
        if (levelInt > StandardLevel.ERROR.intLevel()) {
            return LogLevel.WARN;
        }
        return LogLevel.ERROR;
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable Message message, @Nullable Throwable t) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable CharSequence message, @Nullable Throwable t) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable Object message, @Nullable Throwable t) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Throwable t) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object @Nullable ... params) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0, @Nullable Object p1) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0, @Nullable Object p1, @Nullable Object p2) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0, @Nullable Object p1, @Nullable Object p2, @Nullable Object p3) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0, @Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0, @Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0, @Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5, @Nullable Object p6) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0, @Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5, @Nullable Object p6, @Nullable Object p7) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0, @Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5, @Nullable Object p6, @Nullable Object p7, @Nullable Object p8) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0, @Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5, @Nullable Object p6, @Nullable Object p7, @Nullable Object p8, @Nullable Object p9) {
        return DISPATCHER.isEnabled(name, translateLog4jLevel(level), marker == null ? null : marker.getName());
    }

    @Override
    public void logMessage(String fqcn, Level level, @Nullable Marker marker, Message message, @Nullable Throwable t) {
        LogLevel lvl = translateLog4jLevel(level);
        if (DISPATCHER.isLevelEnabled(lvl)) {
            String mrk = marker == null ? null : marker.getName();
            ResourcePool.Lease<Log4JMessageFormatter> lease = FORMATTERS.tryAcquire();
            if (lease != null) {
                try (var formatterLease = lease) {
                    Log4JMessageFormatter formatter = formatterLease.get();
                    formatter.setMessage(message);
                    DISPATCHER.filterAndDispatch(System.currentTimeMillis(), name, lvl, mrk, MDC_INSTANCE, LOCATION_RESOLVER, formatter, t);
                }
            } else {
                // re-entrant logging call
                SLB4J.logInternal(LogLevel.DEBUG, "reentrant logging call detected, using temporary formatter!");
                Log4JMessageFormatter formatter = new Log4JMessageFormatter();
                formatter.setMessage(message);
                DISPATCHER.filterAndDispatch(System.currentTimeMillis(), name, lvl, mrk, MDC_INSTANCE, LOCATION_RESOLVER, formatter, t);
            }
        }
    }

    @Override
    public Level getLevel() {
        return Level.ALL;
    }
}
