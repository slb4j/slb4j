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
package org.slb4j.dispatcher;

import org.slb4j.LocationResolver;
import org.slb4j.LogDispatcher;
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.SLB4J;
import org.slb4j.MDC;
import org.slb4j.support.StackWalkerLocationResolver;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * A centralized dispatcher for handling and processing log events across different logging frameworks.
 * <p>
 * The CommonDispatcher acts as a bridge between multiple logging APIs, providing unified log event
 * dispatching to registered handlers. It supports the logging frameworks Log4j, SLF4J, Java Util Logging (JUL),
 * and Jakarta Commons Logging (JCL), enabling consistent processing of log events regardless of the source.
 * <p>
 * The class follows a singleton pattern to ensure a single instance is used throughout the application.
 */
public final class UniversalDispatcher implements LogDispatcher {

    /**
     * A private static final class that holds a singleton instance of {@link UniversalDispatcher}.
     * This implementation leverages the "Initialization-on-demand holder idiom" to ensure
     * thread-safe, lazy initialization of the singleton instance.
     */
    private static final class SingletonHolder {
        private static final UniversalDispatcher INSTANCE = new UniversalDispatcher();
    }

    /**
     * Provides the singleton instance of the CommonDispatcher.
     * <p>
     * This method ensures a single instance of CommonDispatcher is shared
     * across the application using a holder class for lazy initialization.
     *
     * @return the singleton instance of CommonDispatcher
     */
    public static UniversalDispatcher getInstance() {
        return SingletonHolder.INSTANCE;
    }

    // definition of location resolvers
    private static final LocationResolver LOCATION_RESOLVER_LOG4J = new StackWalkerLocationResolver(SLB4J.class.getPackageName(), "org.apache.logging");

    private LogFilter filter = LogFilter.allPass();

    /**
     * A thread-safe list of weak references to LogHandler instances. This guarantees that the handlers
     * can be accessed concurrently without external synchronization and minimizes memory retention by
     * allowing garbage collection of handlers no longer in use.
     */
    private final List<WeakReference<LogHandler>> handlers = new CopyOnWriteArrayList<>();

    /**
     * Default constructor for the CommonDispatcher class.
     * <p>
     * This constructor initializes an instance of the CommonDispatcher class
     * without any specific configuration or parameters.
     */
    public UniversalDispatcher() {
        // nothing to do
    }

    /**
     * Checks if logging is enabled for the specified log level.
     *
     * @param lvl the log level to check for enablement
     * @return true if logging is enabled for the given log level, false otherwise
     */
    public boolean isLevelEnabled(LogLevel lvl) {
        return filter.isLevelEnabled(lvl);
    }

    /**
     * Checks if a specific logging configuration is enabled based on the provided name, log level, and optional marker.
     *
     * @param name the name of the logger to check
     * @param logLevel the log level to evaluate
     * @param marker an optional marker to further filter the logging configuration; may be null
     * @return true if the logging configuration is enabled, false otherwise
     */
    public boolean isEnabled(String name, LogLevel logLevel, @Nullable String marker) {
        return isLevelEnabled(logLevel) && filter.isEnabled(name, logLevel, marker);
    }

    @Override
    public void addLogHandler(LogHandler handler) {
        handlers.add(new WeakReference<>(handler));
    }

    @Override
    public synchronized void removeLogHandler(LogHandler handler) {
        handlers.removeIf(h -> h.get() == handler);
    }

    @Override
    public void setFilter(LogFilter filter) {
        this.filter = filter;
    }

    @Override
    public LogFilter getFilter() {
        return filter;
    }

    @Override
    public Collection<LogHandler> getLogHandlers() {
        return List.copyOf(handlers.stream().map(WeakReference::get).filter(Objects::nonNull).toList());
    }

    /**
     * Filters and dispatches log events to registered {@link LogHandler} instances.
     * This method evaluates if a log event passes the predefined filter criteria
     * and dispatches it to all handlers enabled for the specified log level.
     *
     * @param instant the timestamp of the log event; must not be null
     * @param loggerName the name of the logger emitting the event; must not be null
     * @param lvl the level of the log event; must not be null
     * @param mrk an optional marker associated with the log event; may be null
     * @param mdc the MDC context for the log event; may be null
     * @param locationResolver the resolver for determining the log event's location information; must not be null
     * @param msg the supplier for the log message; must not be null
     * @param t an optional {@code Throwable} associated with the log event; may be null
     */
    public void filterAndDispatch(Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver locationResolver, Supplier<String> msg, @Nullable Throwable t) {
        if (filter.test(instant, loggerName, lvl, mrk, mdc, msg, t)) {
            for (WeakReference<LogHandler> handlerRef : handlers) {
                LogHandler handler = handlerRef.get();
                if (handler != null && handler.isEnabled(lvl)) {
                    handler.handle(instant, loggerName, lvl, mrk, mdc, locationResolver, msg, t);
                }
            }
        }
    }

}
