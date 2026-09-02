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

import org.junit.jupiter.api.Test;
import org.slb4j.LogLevel;
import org.slb4j.ext.LogBuffer;
import org.slb4j.ext.LogEntry;
import org.slb4j.ext.LogEntryFilter;
import org.slb4j.filter.LogLevelFilter;

import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableRowSorter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTableModelTest {

    @Test
    void removesTheEvictedRowBeforeAnnouncingTheReplacement() throws Exception {
        LogBuffer buffer = new LogBuffer("test", 2);
        LogTableModel model = new LogTableModel(buffer);
        AtomicInteger rowCountAfterDelete = new AtomicInteger(-1);
        model.addTableModelListener(event -> {
            if (event.getType() == TableModelEvent.DELETE) {
                rowCountAfterDelete.set(model.getRowCount());
            }
        });

        add(buffer, "first");
        add(buffer, "second");
        drainEventQueue();

        add(buffer, "third");
        drainEventQueue();

        assertEquals(1, rowCountAfterDelete.get(),
                "the model must expose the post-deletion row count before firing an insertion");
    }

    @Test
    void displaysTraceAndDebugEntriesAfterBufferRollover() throws Exception {
        LogBuffer buffer = new LogBuffer("test", 2);
        AtomicReference<LogTableModel> modelReference = new AtomicReference<>();
        AtomicReference<TableRowSorter<LogTableModel>> sorterReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            LogTableModel model = new LogTableModel(buffer);
            TableRowSorter<LogTableModel> sorter = new TableRowSorter<>(model);
            LogEntryFilter levelFilter = LogEntryFilter.forFilter(LogLevelFilter.pass(LogLevel.TRACE));
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends LogTableModel, ? extends Integer> entry) {
                    LogEntry logEntry = entry.getModel().getEntry(entry.getIdentifier());
                    return logEntry != null && levelFilter.test(logEntry);
                }
            });
            JTable table = new JTable(model);
            table.setRowSorter(sorter);
            modelReference.set(model);
            sorterReference.set(sorter);
        });

        add(buffer, LogLevel.DEBUG, "first");
        add(buffer, LogLevel.TRACE, "second");
        drainEventQueue();
        add(buffer, LogLevel.DEBUG, "third");
        drainEventQueue();

        SwingUtilities.invokeAndWait(() -> {
            LogTableModel model = modelReference.get();
            TableRowSorter<LogTableModel> sorter = sorterReference.get();
            assertEquals(2, sorter.getViewRowCount());
            assertEquals("second", model.getEntry(sorter.convertRowIndexToModel(0)).message());
            assertEquals("third", model.getEntry(sorter.convertRowIndexToModel(1)).message());
        });
    }

    @Test
    void displaysEntriesAddedBeforeItRegistersAsAListener() throws Exception {
        LogBuffer buffer = new LogBuffer("test", 2);
        add(buffer, "first");
        LogTableModel model = new LogTableModel(buffer);
        drainEventQueue();

        assertEquals(1, model.getRowCount());
        assertEquals("first", model.getEntry(0).message());
    }

    @Test
    void flushesAnEntryAddedWhileThePreviousUpdateIsBeingApplied() throws Exception {
        BlockingSnapshotLogBuffer buffer = new BlockingSnapshotLogBuffer();
        LogTableModel model = new LogTableModel(buffer);

        add(buffer, "first");
        assertTrue(buffer.snapshotCaptured.await(5, TimeUnit.SECONDS), "first update was not started");

        add(buffer, "second");
        buffer.allowSnapshotToFinish.countDown();

        assertTrue(waitUntil(() -> model.getRowCount() == 2, 5, TimeUnit.SECONDS),
                "the second entry was not flushed without another log message");
        assertEquals("second", model.getEntry(1).message());
    }

    private static void add(LogBuffer buffer, String message) {
        add(buffer, LogLevel.INFO, message);
    }

    private static void add(LogBuffer buffer, LogLevel level, String message) {
        buffer.handle(System.currentTimeMillis(), "test", level, null, null, null, message, null);
    }

    private static void drainEventQueue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Reaching this task means that all previously queued updates have run.
        });
    }

    @SuppressWarnings("java:S2925") // accepted for test code
    private static boolean waitUntil(java.util.function.BooleanSupplier condition, long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private static final class BlockingSnapshotLogBuffer extends LogBuffer {
        private final CountDownLatch snapshotCaptured = new CountDownLatch(1);
        private final CountDownLatch allowSnapshotToFinish = new CountDownLatch(1);
        private boolean firstSnapshot = true;

        private BlockingSnapshotLogBuffer() {
            super("test", 10);
        }

        @Override
        public BufferState getBufferState() {
            BufferState state = super.getBufferState();
            synchronized (this) {
                if (!firstSnapshot) {
                    return state;
                }
                firstSnapshot = false;
            }
            snapshotCaptured.countDown();
            try {
                if (!allowSnapshotToFinish.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("test did not release the first snapshot");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            return state;
        }
    }
}
