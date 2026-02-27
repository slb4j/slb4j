package org.slb4j.frontend.log4j;

import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slb4j.LogLevel;
import org.slb4j.SLB4J;

import java.util.function.Supplier;

final class Log4JMessageFormatter implements Supplier<CharSequence> {
    private static final int INITIAL_CAPACITY = 128;
    private static final int MAX_CAPACITY = 1024;

    private static final Supplier<CharSequence> EMPTY_STRING_SUPPLIER = ""::toString;

    private final StringBuilder buffer = new StringBuilder(INITIAL_CAPACITY);
    private final Supplier<CharSequence> initialSupplier = this::getInitial;
    private final Supplier<CharSequence> bufferSupplier = this::getBuffer;

    private @Nullable Message message;
    private @Nullable Supplier<CharSequence> delegate;

    Log4JMessageFormatter() {
        this.message = null;
        this.delegate = EMPTY_STRING_SUPPLIER;
    }

    public void setMessage(@NonNull Message message) {
        this.message = message;
        this.delegate = initialSupplier;
    }

    private StringBuilder getBuffer() {
        return buffer;
    }

    private CharSequence getInitial() {
        switch (message) {
            case StringBuilderFormattable sbf -> {
                sbf.formatTo(buffer);
                message = null;
                delegate = bufferSupplier;
                return buffer;
            }
            case null -> {
                SLB4J.logInternal(LogLevel.WARN, "format() called with message=null!");
                delegate = EMPTY_STRING_SUPPLIER;
                return "";
            }
            default -> {
                String s = message.getFormattedMessage();
                message = null;
                delegate = s::toString;
                return s;
            }
        }
    }

    public void cleanup() {
        delegate = EMPTY_STRING_SUPPLIER;
        message = null;

        buffer.setLength(0);
        if (buffer.capacity() > MAX_CAPACITY) {
            buffer.trimToSize();
        }
    }

    @Override
    public CharSequence get() {
        return delegate.get();
    }
}
