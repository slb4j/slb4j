package org.slb4j.ext.swing;

import org.jspecify.annotations.Nullable;
import org.slb4j.ext.LogBuffer;
import org.slb4j.ext.LogEntry;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class represents a table model for displaying log entries in a Swing LogPane.
 */
final class LogTableModel extends AbstractTableModel implements LogBuffer.LogBufferListener {
    private static final long REST_TIME_IN_MS = 50;

    private volatile List<@Nullable LogEntry> data = Collections.emptyList();
    private final AtomicLong totalAdded = new AtomicLong(0);
    private final AtomicLong totalRemoved = new AtomicLong(0);

    private final ReadWriteLock updateLock = new ReentrantReadWriteLock();
    private final Lock updateWriteLock = updateLock.writeLock();
    private final Condition updatesAvailableCondition = updateWriteLock.newCondition();

    LogTableModel(LogBuffer buffer) {
        buffer.addLogBufferListener(this);

        Thread updateThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                updateWriteLock.lock();
                try {
                    updatesAvailableCondition.await();

                    SwingUtilities.invokeLater(() -> {
                        LogBuffer.BufferState state = buffer.getBufferState();
                        List<LogEntry> newData = Arrays.asList(state.entries());
                        totalAdded.getAndSet(state.totalAdded());
                        long ta = totalAdded.get();
                        long trOld = totalRemoved.getAndSet(state.totalRemoved());
                        long tr = totalRemoved.get();

                        assert newData.size() == ta - tr;

                        int newSz = newData.size();
                        int oldSz = data.size();
                        int removedRows = (int) Math.min(oldSz, (tr - trOld));
                        int remainingRows = oldSz - removedRows;
                        int addedRows = newSz - remainingRows;

                        data = newData;

                        if (removedRows > 0) {
                            fireTableRowsDeleted(0, removedRows - 1);
                        }
                        if (addedRows > 0) {
                            fireTableRowsInserted(newSz - addedRows, newSz - 1);
                        }
                        if (removedRows == 0 && addedRows == 0 && oldSz == newSz && oldSz > 0) {
                            fireTableDataChanged();
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // ignore
                } finally {
                    updateWriteLock.unlock();
                }
                try {
                    //noinspection BusyWait - to avoid using up all CPU cycles when entries come in fast
                    Thread.sleep(REST_TIME_IN_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "LogTableModel Update Thread");
        updateThread.setPriority(Thread.NORM_PRIORITY - 1);
        updateThread.setDaemon(true);
        updateThread.start();
    }

    @Override
    public int getRowCount() {
        return data.size();
    }

    @Override
    public int getColumnCount() {
        return 4; // Time, Level, Logger, Message
    }

    @Override
    public @Nullable Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= data.size()) {
            return null;
        }
        return data.get(rowIndex);
    }

    public @Nullable LogEntry getEntry(int rowIndex) {
        return data.get(rowIndex);
    }

    @Override
    public void entries(int removed, int added) {
        updateWriteLock.lock();
        try {
            updatesAvailableCondition.signalAll();
        } finally {
            updateWriteLock.unlock();
        }
    }

    @Override
    public void clear() {
        updateWriteLock.lock();
        try {
            updatesAvailableCondition.signalAll();
        } finally {
            updateWriteLock.unlock();
        }
    }
}
