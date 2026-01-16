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

    /**
     * The buffer that provides the underlying character storage for this {@code IoStringBuilder}.
     */
    private CharBuffer buffer;

    /**
     * Constructs a new {@code IoStringBuilder} with the specified initial capacity.
     *
     * @param initialCapacity the initial capacity of the underlying {@code CharBuffer}
     *                        to allocate for the string builder. Must be a non-negative value.
     * @throws IllegalArgumentException if the specified {@code initialCapacity} is negative
     */
    public IoStringBuilder(int initialCapacity) {
        this.buffer = CharBuffer.allocate(Math.max(initialCapacity, MIN_CAPACITY));
    }

    /**
     * Ensures that the capacity of the underlying {@code CharBuffer} is sufficient
     * to accommodate the specified number of additional characters. If the
     * required capacity exceeds the current capacity, a new buffer is allocated,
     * and the existing content is transferred to the new buffer.
     *
     * @param needed the number of additional characters that the buffer must be
     *                able to accommodate. Must be a non-negative value.
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
        if (csq == null) {
            csq = "null";
        }

        int len = end - start;
        ensureCapacity(len);
        switch (csq) {
            case String s -> buffer.put(s, start, end);
            case StringBuilder sb -> {
                // HeapCharBuffer implements a fast path for StringBuilder
                buffer.append(sb, start, end);
            }
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
     */
    public void reset(int maxCapacity) {
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
     * @param end the ending index of the subsequence (exclusive)
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