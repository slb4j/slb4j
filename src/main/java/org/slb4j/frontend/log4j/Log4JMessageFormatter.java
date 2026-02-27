package org.slb4j.frontend.log4j;

import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.jspecify.annotations.Nullable;
import org.slb4j.LogLevel;
import org.slb4j.SLB4J;

import java.util.function.Supplier;

/**
 * A utility class for formatting Log4J messages efficiently by leveraging a reusable buffer and delegate pattern.
 * This class implements the {@link Supplier} interface to provide formatted log messages dynamically.
 * <p>
 * The formatter is stateful and designed to manage a {@link Message} object as its formatting target.
 * Internally, it employs an efficient {@link StringBuilder} buffer for managing string concatenations
 * and minimizes memory allocations by reusing resources where possible.
 */
public final class Log4JMessageFormatter implements Supplier<CharSequence> {
    private static final int INITIAL_CAPACITY = 128;
    private static final int MAX_CAPACITY = 1024;

    private static final Supplier<CharSequence> EMPTY_STRING_SUPPLIER = ""::toString;

    private final StringBuilder buffer = new StringBuilder(INITIAL_CAPACITY);
    private final Supplier<CharSequence> initialSupplier = this::getInitial;
    private final Supplier<CharSequence> bufferSupplier = this::getBuffer;

    private @Nullable Message message;
    private @Nullable Supplier<CharSequence> delegate;

    /**
     * Initializes a new instance of the {@code Log4JMessageFormatter} class.
     * This constructor sets up the formatter with default values.
     *
     * The formatter is initialized with:
     * - A {@code null} message, indicating no initial formatting target.
     * - A delegate pointing to an empty string supplier, ensuring that calls to {@code get()} return an empty string until a message is set.
     */
    Log4JMessageFormatter() {
        this.message = null;
        this.delegate = EMPTY_STRING_SUPPLIER;
    }

    /**
     * Sets the {@link Message} object to be used as the target for formatting operations.
     * This method initializes the internal delegate to point to the initial message-processing logic,
     * ensuring that the new {@link Message} is correctly formatted on subsequent {@code get()} calls.
     *
     * @param message the {@link Message} to be set as the formatting target.
     *                If {@code null}, subsequent operations may refer to the default behavior of the formatter.
     */
    public void setMessage(Message message) {
        this.message = message;
        this.delegate = initialSupplier;
    }

    private StringBuilder getBuffer() {
        return buffer;
    }

    /**
     * Provides the initial formatted representation of the message, depending on its type.
     * The method determines the message's format representation, processes it accordingly,
     * and assigns updates the delegate to return the already formatted message.
     *
     * @return the formatted message as a {@code CharSequence}
     */
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

    /**
     * Resets the state of the {@code Log4JMessageFormatter} to its default values.
     */
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
