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
package org.slb4j.ext.fx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slb4j.LogLevel;
import org.slb4j.ext.LogBuffer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.SAME_THREAD)
class LogEntriesObservableListTest extends FxTestBase {

    @Test
    void flushesAnEntryAddedWhileThePreviousUpdateIsBeingApplied() throws Throwable {
        BlockingSnapshotLogBuffer buffer = new BlockingSnapshotLogBuffer();
        AtomicReference<LogEntriesObservableList> entries = new AtomicReference<>();
        runOnFxThreadAndWait(() -> entries.set(new LogEntriesObservableList(buffer)));
        runOnFxThreadAndWait(() -> {
            // Ensure the initial empty snapshot has completed.
        });

        buffer.blockNextSnapshot();
        add(buffer, "first");
        assertTrue(buffer.snapshotCaptured.await(5, TimeUnit.SECONDS), "first update was not started");

        add(buffer, "second");
        buffer.allowSnapshotToFinish.countDown();

        assertTrue(waitUntil(() -> entryCount(entries.get()) == 2, 5, TimeUnit.SECONDS),
                "the second entry was not flushed without another log message");
        runOnFxThreadAndWait(() -> assertEquals("second", entries.get().get(1).message()));
    }

    @Test
    void displaysEntriesAddedBeforeItRegistersAsAListener() throws Throwable {
        LogBuffer buffer = new LogBuffer("test", 2);
        add(buffer, "first");

        AtomicReference<LogEntriesObservableList> entries = new AtomicReference<>();
        runOnFxThreadAndWait(() -> entries.set(new LogEntriesObservableList(buffer)));
        runOnFxThreadAndWait(() -> {
            assertEquals(1, entries.get().size());
            assertEquals("first", entries.get().get(0).message());
        });
    }

    private static void add(LogBuffer buffer, String message) {
        buffer.handle(System.currentTimeMillis(), "test", LogLevel.INFO, null, null, null, message, null);
    }

    @SuppressWarnings("java:S2925") // accepted for test code
    private static boolean waitUntil(ThrowingBooleanSupplier condition, long timeout, TimeUnit unit) throws Throwable {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private static int entryCount(LogEntriesObservableList entries) throws Exception {
        AtomicReference<Integer> count = new AtomicReference<>();
        runOnFxThreadAndWait(() -> count.set(entries.size()));
        return count.get();
    }

    @FunctionalInterface
    private interface ThrowingBooleanSupplier {
        boolean getAsBoolean() throws Throwable;
    }

    private static final class BlockingSnapshotLogBuffer extends LogBuffer {
        private final AtomicBoolean blockNextSnapshot = new AtomicBoolean();
        private final CountDownLatch snapshotCaptured = new CountDownLatch(1);
        private final CountDownLatch allowSnapshotToFinish = new CountDownLatch(1);

        private BlockingSnapshotLogBuffer() {
            super("test", 10);
        }

        private void blockNextSnapshot() {
            blockNextSnapshot.set(true);
        }

        @Override
        public BufferState getBufferState() {
            BufferState state = super.getBufferState();
            if (!blockNextSnapshot.compareAndSet(true, false)) {
                return state;
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
