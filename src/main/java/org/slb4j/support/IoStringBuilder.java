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
package org.slb4j.support;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.Objects;

/**
 * A high-performance, auto-resizing {@link Appendable} designed for
 * zero-allocation intermediate text buffering.
 * Unlike StringBuilder, this class avoids internal String copies when
 * appending to a {@link Writer}.
 * <p>
 * <strong>Note:</strong> This class is not thread-safe!
 */
public final class IoStringBuilder implements Appendable {

    /**
     * The minimum capacity of the underlying character buffer used by the {@code IoStringBuilder}.
     */
    private static final int MIN_CAPACITY = 16;

    private final int initialCapacity;
    /**
     * The buffer that provides the underlying character storage for this {@code IoStringBuilder}.
     */
    private CharBuffer buffer;

    /**
     * Constructs a new {@code IoStringBuilder} with a default initial capacity.
     * <p>
     * This constructor initializes the underlying {@code CharBuffer} to the default
     * minimum capacity defined by {@code MIN_CAPACITY}.
     */
    public IoStringBuilder() {
        this(MIN_CAPACITY);
    }

    /**
     * Constructs a new {@code IoStringBuilder} with the specified initial capacity.
     *
     * @param initialCapacity the initial capacity of the underlying {@code CharBuffer}
     *                        to allocate for the string builder. Must be a non-negative value.
     * @throws IllegalArgumentException if the specified {@code initialCapacity} is negative
     */
    public IoStringBuilder(int initialCapacity) {
        this.initialCapacity = Math.max(initialCapacity, MIN_CAPACITY);
        this.buffer = CharBuffer.allocate(initialCapacity);
    }

    /**
     * Ensures that the capacity of the underlying {@code CharBuffer} is sufficient
     * to accommodate the specified number of additional characters. If the
     * required capacity exceeds the current capacity, a new buffer is allocated,
     * and the existing content is transferred to the new buffer.
     *
     * @param needed the number of additional characters that the buffer must be
     *               able to accommodate. Must be a non-negative value.
     * @throws IllegalArgumentException if {@code needed} is negative
     */
    private void ensureCapacity(int needed) {
        if (buffer.remaining() < needed) {
            int newCapacity = Math.max(buffer.capacity() * 2, buffer.position() + needed);
            CharBuffer newBuffer = CharBuffer.allocate(newCapacity);

            buffer.flip();
            newBuffer.put(buffer);
            this.buffer = newBuffer;
        }
    }

    /**
     * Appends the string representation of the specified integer to this {@code IoStringBuilder}.
     *
     * @param n the integer to be appended
     * @return this {@code IoStringBuilder} instance, allowing for method chaining
     */
    public IoStringBuilder append(int n) {
        return append(Integer.toString(n));
    }

    /**
     * Appends the string representation of the specified {@code long} value to this {@code IoStringBuilder}.
     *
     * @param n the {@code long} value whose string representation is to be appended
     * @return this {@code IoStringBuilder} instance with the appended content
     */
    public IoStringBuilder append(long n) {
        return append(Long.toString(n));
    }

    /**
     * Appends the string representation of the specified floating-point value to this {@code IoStringBuilder}.
     *
     * @param f the floating-point value to append
     * @return this {@code IoStringBuilder} instance with the appended value
     */
    public IoStringBuilder append(float f) {
        return append(Float.toString(f));
    }

    /**
     * Appends the string representation of the specified double value to this {@code IoStringBuilder}.
     *
     * @param f the double value to be appended
     * @return this {@code IoStringBuilder} instance, allowing for method chaining
     */
    public IoStringBuilder append(double f) {
        return append(Double.toString(f));
    }

    /**
     * Appends the string representation of the specified boolean value to this instance.
     *
     * @param b the boolean value to append
     * @return this {@code IoStringBuilder} instance, allowing method chaining
     */
    public IoStringBuilder append(boolean b) {
        return append(String.valueOf(b));
    }

    @Override
    public IoStringBuilder append(char c) {
        ensureCapacity(1);
        buffer.put(c);
        return this;
    }

    @Override
    public IoStringBuilder append(@Nullable CharSequence csq) {
        if (csq == null) {
            csq = "null";
        }
        return append(csq, 0, csq.length());
    }

    @Override
    public IoStringBuilder append(@Nullable CharSequence csq, int start, int end) {
        int len = end - start;
        if (len == 0) {
            return this;
        }

        if (csq == null) {
            csq = "null";
        }

        ensureCapacity(len);
        switch (csq) {
            case String s -> buffer.put(s, start, end);
            case StringBuilder sb ->
                // HeapCharBuffer implements a fast path for StringBuilder
                    buffer.append(sb, start, end);
            case CharBuffer cb -> {
                // Optimized path for other NIO buffers
                CharBuffer src = cb.duplicate();
                src.position(start).limit(end);
                buffer.put(src);
            }
            default -> {
                // Guaranteed zero-allocation fallback
                // reason for manual copy: some implementations of append call String.valueOf(csq) internally
                for (int i = start; i < end; i++) {
                    buffer.put(csq.charAt(i));
                }
            }
        }
        return this;
    }

    /**
     * Writes the content of the internal buffer to the specified {@link Writer}.
     * <p>
     * <strong>Note:</strong> After writing, the buffer is cleared for reuse.
     *
     * @param writer the {@link Writer} to which the contents of the buffer will
     *               be written
     * @throws IOException if an I/O error occurs while writing to the writer
     */
    public void writeTo(Writer writer) throws IOException {
        buffer.flip();
        if (buffer.hasArray()) {
            // Direct array access: The fastest possible way to move data to a Writer
            writer.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
        } else {
            writer.append(buffer);
        }
        buffer.clear();
    }

    /**
     * Returns the number of characters written to the buffer.
     *
     * @return the number of characters written
     */
    public int length() {
        return buffer.position();
    }

    /**
     * Checks if the {@code IoStringBuilder} is empty.
     *
     * @return {@code true} if the buffer is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return buffer.position() == 0;
    }

    /**
     * Returns the character at the specified index.
     *
     * @param index the zero-based index of the character to return
     * @return the character at the specified index
     * @throws IndexOutOfBoundsException if the index is negative or greater than or equal to the length of the buffer
     */
    public char charAt(int index) {
        Objects.checkIndex(index, buffer.position());
        return buffer.get(index);
    }

    /**
     * Clears this instance for reuse.
     * <p>
     * The internal buffer will retain its current capacity.
     */
    public void reset() {
        buffer.clear();
    }

    /**
     * Clears this instance for reuse.
     * <p>
     * The internal buffer will be trimmed if the capacity exceeds the provided {@code maxCapacity}.
     *
     * @param maxCapacity the maximum capacity to retain; if the current buffer capacity exceeds this value,
     *                    a new buffer with the specified maximum capacity will be allocated
     */
    public void reset(int maxCapacity) {
        maxCapacity = Math.max(maxCapacity, initialCapacity);
        if (buffer.capacity() <= maxCapacity) {
            buffer.clear();
        } else {
            buffer = CharBuffer.allocate(Math.max(maxCapacity, MIN_CAPACITY));
        }
    }

    @Override
    public String toString() {
        return toString(0, buffer.position());
    }

    /**
     * Returns a string representation of the subsequence of characters
     * within the specified range.
     *
     * @param start the starting index of the subsequence (inclusive)
     * @param end   the ending index of the subsequence (exclusive)
     * @return the string representation of the characters in the specified range
     * @throws IndexOutOfBoundsException if {@code start} is negative,
     *                                   {@code end} is greater than the length of the buffer,
     *                                   or {@code start} is greater than {@code end}
     */
    public String toString(int start, int end) {
        Objects.checkFromToIndex(start, end, buffer.position());
        return buffer.slice(start, end).toString();
    }
}