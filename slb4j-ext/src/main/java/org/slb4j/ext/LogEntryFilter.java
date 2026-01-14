package org.slb4j.ext;

import org.jspecify.annotations.Nullable;
import org.slb4j.LogFilter;
import org.slb4j.LogLevel;
import org.slb4j.MDC;

import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The {@code LogEntryFilter} class is an implementation of the {@link LogFilter} interface
 * and the {@link Predicate} functional interface.
 * <p>
 * It provides a wrapper around an existing {@code LogFilter}, delegating filtering logic
 * to the wrapped filter while also implementing methods required by the {@code Predicate}
 * interface to seamlessly work with standard Java functional constructs.
 * <p>
 * This class is immutable and thread-safe, ensuring consistent behavior across concurrent
 * invocations.
 */
public final class LogEntryFilter implements LogFilter, Predicate<LogEntry> {

    private final LogFilter filter;

    /**
     * Converts a given {@code LogFilter} instance into a {@code LogEntryFilter}.
     * <p>
     * If the provided filter is already an instance of {@code LogEntryFilter},
     * it is returned directly; otherwise, a new {@code LogEntryFilter} is created
     * to wrap the given {@code LogFilter}.
     *
     * @param filter the {@code LogFilter} to be converted or wrapped, must not be null
     * @return a {@code LogEntryFilter} instance based on the provided {@code LogFilter}
     */
    public static LogEntryFilter forFilter(LogFilter filter) {
        return filter instanceof LogEntryFilter llf ? llf : new LogEntryFilter(filter);
    }

    private LogEntryFilter(LogFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean test(LogEntry entry) {
        return test(entry.time(), entry.logger(), entry.level(), entry.marker(), entry.mdc(), entry::message, entry.throwable());
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public boolean test(Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, Supplier<@Nullable String> msg, @Nullable Throwable t) {
        return filter.test(instant, loggerName, lvl, mrk, mdc, msg, t);
    }
}
