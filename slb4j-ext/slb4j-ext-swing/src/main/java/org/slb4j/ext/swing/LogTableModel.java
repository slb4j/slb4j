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
package org.slb4j.ext.swing;

import org.jspecify.annotations.Nullable;
import org.slb4j.ext.LogBuffer;
import org.slb4j.ext.LogEntry;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class represents a table model for displaying log entries in a Swing LogPane.
 */
final class LogTableModel extends AbstractTableModel implements LogBuffer.LogBufferListener {
    private final LogBuffer buffer;
    private volatile List<@Nullable LogEntry> data = Collections.emptyList();
    private final AtomicLong totalAdded = new AtomicLong(0);
    private final AtomicLong totalRemoved = new AtomicLong(0);
    private final AtomicBoolean updateScheduled = new AtomicBoolean();
    private final AtomicLong updateGeneration = new AtomicLong();

    LogTableModel(LogBuffer buffer) {
        this.buffer = buffer;
        buffer.addLogBufferListener(this);
        // Entries can be written between the LogBuffer being registered with the
        // dispatcher and this model registering as its listener. No notification
        // is delivered for those entries, so load the initial snapshot explicitly.
        scheduleUpdate();
    }

    private void scheduleUpdate() {
        if (updateScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::update);
        }
    }

    private void update() {
        long generation = updateGeneration.get();
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

        updateScheduled.set(false);
        // An update notification received while this update was running may not have
        // scheduled another EDT task. Queue one now so that its entries are flushed.
        if (updateGeneration.get() != generation) {
            scheduleUpdate();
        }
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
        updateGeneration.incrementAndGet();
        scheduleUpdate();
    }

    @Override
    public void clear() {
        updateGeneration.incrementAndGet();
        scheduleUpdate();
    }
}
