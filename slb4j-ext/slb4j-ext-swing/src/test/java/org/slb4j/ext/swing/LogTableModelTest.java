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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTableModelTest {

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
        buffer.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, message, null);
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
