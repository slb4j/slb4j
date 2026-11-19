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

import org.slb4j.Location;
import org.slb4j.LocationResolver;
import org.slb4j.LogDispatcher;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.jspecify.annotations.Nullable;
import org.slb4j.SLB4J;
import org.slb4j.filter.LoggerNamePrefixFilter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    /** The active filter and handler snapshot. */
    private final AtomicReference<DispatchState> state;

    /** Configuration changes are off the dispatch hot path. */
    private final Object configurationLock = new Object();

    /**
     * Default constructor for the CommonDispatcher class.
     * <p>
     * This constructor initializes an instance of the CommonDispatcher class
     * without any specific configuration or parameters.
     */
    public UniversalDispatcher() {
        HandlerSnapshot emptyHandlers = new HandlerSnapshot(List.of());
        this.state = new AtomicReference<>(new DispatchState(new LoggerNamePrefixFilter("root"), emptyHandlers));
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "UniversalDispatcher-ShutdownHook"));
    }

    /**
     * Shuts down the {@code UniversalDispatcher} by invoking the shutdown process
     * for each registered {@code LogHandler} instance. This method ensures that
     * all associated resources are released, cleanup tasks are performed, and any
     * finalization work related to logging is completed.
     * <p>
     * This operation iterates over all the log handlers currently managed by the
     * dispatcher and invokes their respective {@code shutdown()} methods to finalize
     * their activities. After this method is called, the dispatcher and its handlers
     * should not be expected to process any further log entries.
     */
    public void shutdown() {
        clearLogHandlers();
    }

    /**
     * Checks if logging is enabled for the specified log level.
     *
     * @param lvl the log level to check for enablement
     * @return true if logging is enabled for the given log level, false otherwise
     */
    public boolean isLevelEnabled(LogLevel lvl) {
        return getFilter().isLevelEnabled(lvl);
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
        return getFilter().isEnabled(name, logLevel, marker);
    }

    @Override
    public void addLogHandler(LogHandler handler) {
        synchronized (configurationLock) {
            List<LogHandler> replacement = new ArrayList<>(getLogHandlers());
            replacement.add(handler);
            replaceLogHandlersLocked(replacement);
        }
    }

    @Override
    public void removeLogHandler(LogHandler handler) {
        synchronized (configurationLock) {
            List<LogHandler> replacement = new ArrayList<>();
            for (LogHandler registered : getLogHandlers()) {
                if (registered != handler) {
                    replacement.add(registered);
                }
            }
            replaceLogHandlersLocked(replacement);
        }
    }

    @Override
    public void setFilter(LoggerNamePrefixFilter filter) {
        state.updateAndGet(current -> new DispatchState(filter, current.handlers));
        SLB4J.logInternal(LogLevel.INFO, "Global dispatcher filter set to: %s", filter);
    }

    @Override
    public LoggerNamePrefixFilter getFilter() {
        return state.get().filter;
    }

    @Override
    public LogLevel getRootLevel() {
        return getFilter().getRootLevel();
    }

    @Override
    public void setRootLevel(LogLevel level) {
        getFilter().setRootLevel(level);
    }

    @Override
    public void setLevel(String prefix, LogLevel level) {
        getFilter().setLevel(prefix, level);
    }

    @Override
    public LogLevel getLevel(String prefix) {
        return getFilter().getLevel(prefix);
    }

    /**
     * Removes all registered log handlers.
     */
    public void clearLogHandlers() {
        replaceLogHandlers(List.of());
    }

    /**
     * Atomically replaces the registered handlers and releases handlers that
     * are no longer part of the active set.
     * <p>
     * The dispatcher owns handlers while they are registered. A handler that
     * is present in both sets is retained, which allows a caller to share a
     * handler between successive configurations without closing it during the
     * replacement.
     *
     * @param replacement the complete replacement handler set
     */
    public void replaceLogHandlers(Iterable<? extends LogHandler> replacement) {
        List<LogHandler> newHandlers = new ArrayList<>();
        replacement.forEach(handler -> newHandlers.add(Objects.requireNonNull(handler, "handler")));

        synchronized (configurationLock) {
            replaceLogHandlersLocked(newHandlers);
        }
    }

    /**
     * Replaces the active log handler snapshot with the supplied handlers while preserving
     * the current dispatch filter. The state update is performed atomically so concurrent
     * dispatch operations observe either the previous handler snapshot or the replacement
     * snapshot. After the replacement is installed, handlers from the previous snapshot that
     * are not retained by the new snapshot are retired.
     *
     * @param replacement the complete set of log handlers to install
     */
    private void replaceLogHandlersLocked(List<LogHandler> replacement) {
        HandlerSnapshot newHandlers = new HandlerSnapshot(replacement);
        HandlerSnapshot oldHandlers;

        for (;;) {
            DispatchState current = state.get();
            DispatchState updated = new DispatchState(current.filter, newHandlers);
            if (state.compareAndSet(current, updated)) {
                oldHandlers = current.handlers;
                break;
            }
        }

        oldHandlers.retire(newHandlers);
    }

    @Override
    public SequencedCollection<LogHandler> getLogHandlers() {
        return state.get().handlers.getHandlers();
    }

    /**
     * Filters and dispatches log events to registered {@link LogHandler} instances.
     * This method evaluates if a log event passes the predefined filter criteria
     * and dispatches it to all handlers enabled for the specified log level.
     *
     * @param timestamp the timestamp of the log event (milliseconds since epoch)
     * @param loggerName the name of the logger emitting the event; must not be null
     * @param lvl the level of the log event; must not be null
     * @param mrk an optional marker associated with the log event; may be null
     * @param mdc the MDC context for the log event; may be null
     * @param locationResolver the resolver for determining the log event's location information; must not be null
     * @param msg the supplier for the log message; must not be null
     * @param t an optional {@code Throwable} associated with the log event; may be null
     */
    public void filterAndDispatch(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, MDC mdc, LocationResolver locationResolver, Supplier<CharSequence> msg, @Nullable Throwable t) {
        DispatchState dispatchState = state.get();
        HandlerSnapshot handlers = dispatchState.handlers;
        if (!handlers.enter()) {
            // Configuration changes are rare. If the snapshot was retired
            // between the state read and enter(), use the slow path once.
            synchronized (configurationLock) {
                dispatchState = state.get();
                handlers = dispatchState.handlers;
                if (!handlers.enter()) {
                    return;
                }
            }
        }

        try {
            if (dispatchState.filter.isEnabled(loggerName, lvl, mrk)) {
                CharSequence message = null;
                Location loc =  null;
                for (LogHandler handler : handlers.handlers) {
                    if (handler.isEnabled(lvl)) {
                        if (loc == null && handler.isLocationNeeded()) {
                            loc = locationResolver.resolve();
                        }
                        if (message == null) {
                            message = msg.get();
                        }
                        handler.handle(timestamp, loggerName, lvl, mrk, mdc, loc, message, t);
                    }
                }
            }
        } finally {
            handlers.exit();
        }
    }

    /**
     * Lifecycle snapshot of the dispatcher's registered {@link LogHandler} instances.
     *
     * A snapshot represents the handler set observed by concurrent dispatch operations.
     * Callers enter the snapshot before iterating handlers and exit it when dispatch
     * completes. This reader accounting allows a replaced snapshot to remain available
     * to in-flight dispatch operations while preventing new readers from entering after
     * retirement begins.
     *
     * When retired, the snapshot identifies handlers that are not present in the
     * replacement snapshot by object identity and schedules only those handlers for
     * shutdown. Handler instances shared with the replacement snapshot are retained and
     * are not closed. Shutdown is performed once, either immediately when no readers are
     * active or after the final reader exits.
     *
     * This class is intended for internal use by {@code UniversalDispatcher} to support
     * atomic handler replacement, safe concurrent dispatch, and deterministic cleanup of
     * obsolete handlers.
     */
    private static final class HandlerSnapshot {
        private final CopyOnWriteArrayList<LogHandler> handlers;
        private final AtomicInteger readers = new AtomicInteger();
        private volatile boolean closing;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile List<LogHandler> handlersToClose = List.of();

        private HandlerSnapshot(List<LogHandler> handlers) {
            this.handlers = new CopyOnWriteArrayList<>(handlers);
        }

        private SequencedCollection<LogHandler> getHandlers() {
            return List.copyOf(handlers);
        }

        private boolean enter() {
            if (closing) {
                return false;
            }

            readers.incrementAndGet();
            if (!closing) {
                return true;
            }

            exit();
            return false;
        }

        private void exit() {
            if (readers.decrementAndGet() == 0 && closing) {
                closeHandlers();
            }
        }

        private void retire(HandlerSnapshot replacement) {
            List<LogHandler> toClose = new ArrayList<>();
            IdentityHashMap<LogHandler, Boolean> seen = new IdentityHashMap<>();
            for (LogHandler handler : handlers) {
                if (!containsSameInstance(replacement.handlers, handler) && seen.put(handler, Boolean.TRUE) == null) {
                    toClose.add(handler);
                }
            }

            handlersToClose = List.copyOf(toClose);
            closing = true;
            if (readers.get() == 0) {
                closeHandlers();
            }
        }

        private void closeHandlers() {
            if (closed.compareAndSet(false, true)) {
                for (LogHandler handler : handlersToClose) {
                    shutdownHandler(handler);
                }
            }
        }

        private void shutdownHandler(LogHandler handler) {
            try {
                handler.shutdown();
            } catch (Exception e) {
                SLB4J.logInternal(LogLevel.WARN, "Error shutting down log handler %s: %s", handler.name(), e);
            }
        }

        private static boolean containsSameInstance(CopyOnWriteArrayList<LogHandler> handlers, LogHandler candidate) {
            for (LogHandler handler : handlers) {
                if (handler == candidate) {
                    return true;
                }
            }
            return false;
        }
    }


    private static final class DispatchState {
        private final LoggerNamePrefixFilter filter;
        private final HandlerSnapshot handlers;

        private DispatchState(LoggerNamePrefixFilter filter, HandlerSnapshot handlers) {
            this.filter = filter;
            this.handlers = handlers;
        }
    }

}
