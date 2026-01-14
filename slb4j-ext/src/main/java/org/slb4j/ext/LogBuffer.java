package org.slb4j.ext;

import org.slb4j.Location;
import org.slb4j.LocationResolver;
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.jspecify.annotations.Nullable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A thread-safe log buffer class intended to provide a buffer for log messages
 * to display in GUI applications.
 *
 * <p>All operations are thread-safe. For compound operations requiring consistency
 * across multiple calls, use {@link #getBufferState()} to get an atomic snapshot.
 */
public class LogBuffer implements LogHandler, Externalizable {

    /**
     * The default capacity.
     */
    public static final int DEFAULT_CAPACITY = 10_000;

    private final String name;
    private boolean resolveLocation = false;
    private final transient RingBuffer buffer;
    private final Collection<LogBufferListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong totalAdded = new AtomicLong(0);
    private final AtomicLong totalRemoved = new AtomicLong(0);
    private transient LogFilter filter = LogFilter.allPass();

    private static final class RingBuffer {

        private @Nullable LogEntry[] data;
        private int entries;
        private int start;

        private RingBuffer(int capacity) {
            data = new LogEntry[capacity];
            start = 0;
            entries = 0;
        }

        private boolean put(LogEntry item) {
            int n = capacity();
            if (n == 0) {
                return false;
            }
            if (entries < n) {
                data[index(entries++)] = item;
                return true;
            } else {
                start = (start + 1) % n;
                data[index(entries - 1)] = item;
                return false;
            }
        }

        private void addAll(Collection<? extends LogEntry> items) {
            if (items.isEmpty() || capacity() == 0) {
                return;
            }

            for (LogEntry item : items) {
                if (entries < capacity()) {
                    data[index(entries++)] = item;
                } else {
                    start = (start + 1) % capacity();
                    data[index(entries - 1)] = item;
                }
            }
        }

        private int capacity() {
            return data.length;
        }

        private void clear() {
            if (capacity() > 0) {
                start = entries = 0;
                java.util.Arrays.fill(data, null);
            }
        }

        private LogEntry get(int i) {
            checkIndex(i);
            LogEntry entry = data[index(i)];
            assert entry != null : "internal error: entry must not be null when index is valid, index = " + i;
            return entry;
        }

        private LogEntry[] toArray() {
            LogEntry[] arr = new LogEntry[entries];
            int n1 = Math.min(entries, data.length - start);
            int n2 = entries - n1;
            System.arraycopy(data, start, arr, 0, n1);
            System.arraycopy(data, 0, arr, n1, n2);
            return arr;
        }

        private void setCapacity(int n) {
            if (n != capacity()) {
                var dataNew = new LogEntry[n];
                int itemsToCopy = Math.min(size(), n);
                int startIndex = Math.max(0, size() - n);
                for (int i = 0; i < itemsToCopy; i++) {
                    dataNew[i] = get(startIndex + i);
                }
                data = dataNew;
                start = 0;
                entries = Math.min(entries, n);
            }
        }

        private int size() {
            return entries;
        }

        private void checkIndex(int i) {
            if (i < 0 || i >= size()) {
                throw new IndexOutOfBoundsException("size=" + size() + ", index=" + i);
            }
        }

        private int index(int i) {
            return (start + i) % capacity();
        }

    }

    /**
     * Constructs a new LogBuffer instance with a default name "unnamed" and default capacity.
     * This constructor is needed for serialization.
     */
    public LogBuffer() {
        this("unnamed");
    }

    /**
     * Construct a new LogBuffer instance with default capacity.
     *
     * @param name the name of the buffer
     */
    public LogBuffer(String name) {
        this(name, DEFAULT_CAPACITY);
    }

    /**
     * Construct a new LogBuffer instance.
     *
     * @param name the name of the buffer
     * @param capacity the initial capacity
     */
    public LogBuffer(String name, int capacity) {
        this.name = name;
        this.buffer = new RingBuffer(capacity);
    }

    /**
     * Updates the capacity of the buffer while retaining the existing elements. If the new capacity is less than
     * the current size of the buffer, only the most recent elements within the new capacity are retained.
     *
     * @param n the new capacity for the buffer. Must be a non-negative integer.
     */
    public void setCapacity(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative: " + n);
        }
        synchronized (buffer) {
            int oldSize = buffer.size();
            buffer.setCapacity(n);
            int removed = Math.max(0, oldSize - buffer.size());
            totalRemoved.addAndGet(removed);
            if (removed > 0) {
                listeners.forEach(listener -> listener.entries(removed, 0));
            }
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        BufferState bufferState = getBufferState();
        out.writeInt(bufferState.entries.length);
        for (LogEntry entry : bufferState.entries) {
            // Serialize individual fields to avoid non-serializable components
            out.writeObject(entry.message());
            out.writeObject(entry.logger());
            out.writeObject(entry.time());
            out.writeObject(entry.level());
            out.writeObject(entry.marker());
            out.writeObject(entry.location());

            // Handle throwable carefully
            Throwable t = entry.throwable();
            if (t != null) {
                out.writeBoolean(true);
                out.writeObject(t.getClass().getName());
                out.writeObject(t.getMessage());
                // Serialize stack trace as string to avoid serialization issues
                try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                    t.printStackTrace(pw);
                    out.writeObject(sw.toString());
                }
            } else {
                out.writeBoolean(false);
            }
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int n = in.readInt();
        List<LogEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String message = (String) in.readObject();
            String loggerName = (String) in.readObject();
            Instant time = (Instant) in.readObject();
            LogLevel level = (LogLevel) in.readObject();
            String marker = (String) in.readObject();
            MDC mdc = (MDC) in.readObject();
            Location location = (Location) in.readObject();

            Throwable throwable = null;
            if (in.readBoolean()) {
                String throwableClass = (String) in.readObject();
                String throwableMessage = (String) in.readObject();
                String stackTrace = (String) in.readObject();
                // Create a simple throwable representation
                throwable = new RuntimeException(
                        "Deserialized " + throwableClass + ": " + throwableMessage +
                                "\nOriginal stack trace:\n" + stackTrace);
            }

            entries.add(LogEntry.of(time, loggerName, level, marker, mdc, location, message, throwable));
        }

        // Update buffer state and notify listeners
        int removed;
        synchronized (buffer) {
            removed = buffer.size();
            buffer.clear();
            buffer.addAll(entries);
            totalAdded.set(entries.size());
            totalRemoved.set(0);
        }

        // Notify listeners about the state change
        if (removed > 0) {
            listeners.forEach(LogBufferListener::clear);
        }
        if (!entries.isEmpty()) {
            listeners.forEach(listener -> listener.entries(0, entries.size()));
        }
    }

    /**
     * Add LogBufferListener.
     *
     * @param listener the listener to add
     */
    public void addLogBufferListener(LogBufferListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove LogBufferListener.
     *
     * @param listener the listener to remove
     */
    public void removeLogBufferListener(LogBufferListener listener) {
        listeners.remove(listener);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void handle(Instant instant, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, LocationResolver loc, Supplier<String> msg, @Nullable Throwable t) {
        if (filter.test(instant, loggerName, lvl, mrk, mdc, msg, t)) {
            Location location = resolveLocation ? loc.resolve() : null;
            LogEntry entry = LogEntry.of(instant, loggerName, lvl, mrk, mdc, location, msg.get(), t);
            int removed;
            synchronized (buffer) {
                removed = buffer.put(entry) ? 0 : 1;
                totalAdded.incrementAndGet();
                totalRemoved.addAndGet(removed);
            }

            // Notify listeners outside the buffer synchronization to avoid deadlock
            listeners.forEach(listener -> listener.entries(removed, 1));
        }
    }

    @Override
    public void setFilter(LogFilter filter) {
        this.filter = filter;
    }

    @Override
    public LogFilter getFilter() {
        return filter;
    }

    /**
     * Clear the LogBuffer.
     * Synchronized method that clears the buffer and notifies all registered LogBufferListeners to clear their logs as well.
     */
    public void clear() {
        synchronized (buffer) {
            totalRemoved.addAndGet(buffer.size());
            buffer.clear();
        }

        // Notify listeners outside the buffer synchronization to avoid deadlock
        listeners.forEach(LogBufferListener::clear);
    }

    /**
     * Converts the LogBuffer into an array of LogEntry objects.
     *
     * @return an array of LogEntry objects representing the contents of the LogBuffer
     */
    public LogEntry[] toArray() {
        synchronized (buffer) {
            return buffer.toArray();
        }
    }

    /**
     * Represents the state of a buffer.
     *
     * <p>This record is used to encapsulate the current state of a LogBuffer,
     * including its entries, the total number of log entries that have been
     * removed, and the total number of log entries that have been added.
     *
     * @param entries      the array of LogEntry objects currently in the buffer
     * @param totalRemoved the total count of log entries that have been removed from the buffer
     * @param totalAdded   the total count of log entries that have been added to the buffer
     */
    public record BufferState(LogEntry[] entries, long totalRemoved, long totalAdded) {
        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof BufferState(LogEntry[] entriesOther, long removed, long added))) {
                return false;
            }
            return totalRemoved == removed
                    && totalAdded == added
                    && java.util.Arrays.equals(entries, entriesOther);
        }

        @Override
        public int hashCode() {
            int result = java.util.Arrays.hashCode(entries);
            result = 31 * result + Long.hashCode(totalRemoved);
            result = 17 * result + Long.hashCode(totalAdded);
            return result;
        }

        @Override
        public String toString() {
            return "BufferState[entries=" + java.util.Arrays.toString(entries) +
                    ", totalRemoved=" + totalRemoved +
                    ", totalAdded=" + totalAdded + "]";
        }

        /**
         * Calculates and retrieves the current sequence number of the buffer state.
         * The sequence number is determined by summing the total number of log entries
         * that have been added and removed from the buffer.
         *
         * @return the sequence number, calculated as the sum of totalAdded and totalRemoved values
         */
        public long getSequenceNumber() {
            return totalAdded + totalRemoved;
        }
    }

    /**
     * Retrieves the current state of the buffer, encapsulating the entries within the buffer,
     * the total number of entries removed, and the total number of entries added.
     * This method is thread-safe as it synchronizes on the buffer while performing operations.
     *
     * @return a {@code BufferState} instance containing the current buffer entries,
     *         total removed entries, and total added entries
     */
    public BufferState getBufferState() {
        synchronized (buffer) {
            LogEntry[] array = toArray();
            long r = totalRemoved.get();
            long a = totalAdded.get();
            assert array.length == a - r;
            return new BufferState(array, r, a);
        }
    }

    /**
     * Retrieves the sequence number of the log buffer. The sequence number is calculated
     * as the sum of the total added entries and the total removed entries in the buffer.
     * This method is thread-safe as it synchronizes on the buffer during execution.
     *
     * @return the calculated sequence number of the log buffer as a long value
     */
    public long getSequenceNumber() {
        synchronized (buffer) {
            return totalAdded.get() + totalRemoved.get();
        }
    }

    /**
     * Get the LogEntry at the specified index in the LogBuffer.
     *
     * @param i the index of the LogEntry to retrieve
     * @return the LogEntry at the specified index
     */
    public LogEntry get(int i) {
        synchronized (buffer) {
            return buffer.get(i);
        }
    }

    /**
     * Returns a snapshot of the portion of this LogBuffer between the specified
     * {@code fromIndex}, inclusive, and {@code toIndex}, exclusive. The returned
     * list is a copy and will not reflect subsequent changes to this LogBuffer.
     *
     * @param fromIndex the index of the first LogEntry to be included in the
     *                  returned subList.
     * @param toIndex   the index after the last LogEntry to be included in the
     *                  returned subList.
     * @return a snapshot of the specified range within this LogBuffer.
     * @throws IndexOutOfBoundsException if {@code fromIndex} or {@code toIndex} is
     *                                   out of range (fromIndex &lt; 0 || toIndex &gt; size() || fromIndex &gt; toIndex).
     */
    public List<LogEntry> subList(int fromIndex, int toIndex) {
        LogEntry[] result;
        synchronized (buffer) {
            int len = buffer.size();
            Objects.checkFromToIndex(fromIndex, toIndex, len);
            int sz = toIndex - fromIndex;

            result = new LogEntry[sz];

            // Calculate the position in the ring buffer
            int startPos = buffer.index(fromIndex);
            int capacity = buffer.capacity();

            // Calculate how many elements we can copy in one go before wrapping
            int elementsBeforeWrap = Math.min(sz, capacity - startPos);

            // Copy the first segment (from startPos to end of buffer or until we have all elements)
            System.arraycopy(buffer.data, startPos, result, 0, elementsBeforeWrap);

            // If the range wraps around, copy the remaining elements from the beginning
            if (elementsBeforeWrap < sz) {
                System.arraycopy(buffer.data, 0, result, elementsBeforeWrap, sz - elementsBeforeWrap);
            }
        }

        return Arrays.asList(result);
    }

    /**
     * Appends all LogEntries in this LogBuffer to the specified Appendable.
     *
     * @param app the Appendable to which the LogEntries will be appended
     * @throws IOException if an I/O error occurs while appending the LogEntries
     */
    public void appendTo(Appendable app) throws IOException {
        for (LogEntry entry : toArray()) {
            app.append(entry.toString()).append("\n");
        }
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.NotSerializableException {
        throw new java.io.NotSerializableException(getClass().getName());
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.NotSerializableException {
        throw new java.io.NotSerializableException(getClass().getName());
    }

    /**
     * Interface for Listeners on changes of a {@link LogBuffer} instance's contents.
     */
    public interface LogBufferListener {
        /**
         * Called when multiple entries have been added in a batch.
         *
         * @param removed the number of removed entries
         * @param added   the number added entries
         */
        void entries(int removed, int added);

        /**
         * Called after the buffer has been cleared.
         */
        void clear();
    }
}