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
package org.slb4j.dispatcher;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slb4j.Location;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.LogFilter;
import org.slb4j.filter.LoggerNamePrefixFilter;

import java.util.SequencedCollection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@NullMarked
class UniversalDispatcherTest {

    @Test
    void testAddAndRemoveLogHandler() {
        UniversalDispatcher dispatcher = new UniversalDispatcher();
        LogHandler handler = new TestLogHandler();

        dispatcher.addLogHandler(handler);
        SequencedCollection<LogHandler> handlers = dispatcher.getLogHandlers();
        assertTrue(handlers.contains(handler), "Handler should be present after adding");
        assertEquals(1, handlers.size());

        dispatcher.removeLogHandler(handler);
        handlers = dispatcher.getLogHandlers();
        assertFalse(handlers.contains(handler), "Handler should not be present after removal");
        assertEquals(0, handlers.size());
    }

    @Test
    void testRootFilterVarHandle() {
        UniversalDispatcher dispatcher = new UniversalDispatcher();
        LoggerNamePrefixFilter initialFilter = dispatcher.getFilter();
        assertNotNull(initialFilter);
        assertEquals("root", initialFilter.name());

        LoggerNamePrefixFilter newFilter = new LoggerNamePrefixFilter("newRoot");
        dispatcher.setFilter(newFilter);
        assertSame(newFilter, dispatcher.getFilter());

        dispatcher.setRootLevel(LogLevel.DEBUG);
        assertEquals(LogLevel.DEBUG, dispatcher.getRootLevel());
        assertEquals(LogLevel.DEBUG, newFilter.getRootLevel());

        assertFalse(dispatcher.isLevelEnabled(LogLevel.TRACE));
        assertFalse(dispatcher.isEnabled("com.example", LogLevel.TRACE, null));
        assertTrue(dispatcher.isLevelEnabled(LogLevel.DEBUG));
        assertTrue(dispatcher.isEnabled("com.example", LogLevel.DEBUG, null));

        dispatcher.setRootLevel(LogLevel.TRACE);
        assertEquals(LogLevel.TRACE, dispatcher.getLevel("com.example"));     // should now return the configured level
        assertEquals(LogLevel.TRACE, newFilter.getLevel("com.example")); // should now return the configured level
        // root level was reset to TRACE
        assertTrue(dispatcher.isLevelEnabled(LogLevel.TRACE));
        // isEnabled checks both root level and logger specific level
        assertTrue(dispatcher.isEnabled("com.example", LogLevel.TRACE, null));
        assertTrue(dispatcher.isLevelEnabled(LogLevel.DEBUG));
        assertTrue(dispatcher.isEnabled("com.example", LogLevel.DEBUG, null));
    }

    @Test
    void replacingHandlersShutsDownRemovedHandlersOnce() {
        UniversalDispatcher dispatcher = new UniversalDispatcher();
        TestLogHandler oldHandler = new TestLogHandler();
        TestLogHandler newHandler = new TestLogHandler();

        dispatcher.replaceLogHandlers(java.util.List.of(oldHandler));
        dispatcher.replaceLogHandlers(java.util.List.of(newHandler));

        assertEquals(1, oldHandler.shutdownCount.get());
        assertEquals(0, newHandler.shutdownCount.get());

        // Replacing with the same handler does not close a shared instance.
        dispatcher.replaceLogHandlers(java.util.List.of(newHandler));
        assertEquals(0, newHandler.shutdownCount.get());

        dispatcher.shutdown();
        dispatcher.shutdown();
        assertEquals(1, newHandler.shutdownCount.get());
    }

    @Test
    void replacementDoesNotWaitForInFlightDispatch() throws Exception {
        UniversalDispatcher dispatcher = new UniversalDispatcher();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TestLogHandler oldHandler = new TestLogHandler(entered, release);

        dispatcher.setRootLevel(LogLevel.TRACE);
        dispatcher.replaceLogHandlers(java.util.List.of(oldHandler));
        Thread dispatch = new Thread(() -> dispatcher.filterAndDispatch(
                System.currentTimeMillis(), "test", LogLevel.INFO, null, null,
                () -> null, () -> "message", null));
        dispatch.start();

        assertTrue(entered.await(5, TimeUnit.SECONDS));
        dispatcher.replaceLogHandlers(java.util.List.of());
        assertEquals(0, oldHandler.shutdownCount.get());

        release.countDown();
        dispatch.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(dispatch.isAlive());
        assertEquals(1, oldHandler.shutdownCount.get());
    }

    private static class TestLogHandler implements LogHandler {
        private LogFilter filter = LogFilter.allPass();
        private final AtomicInteger shutdownCount = new AtomicInteger();
        private final @Nullable CountDownLatch entered;
        private final @Nullable CountDownLatch release;

        TestLogHandler() {
            this(null, null);
        }

        TestLogHandler(@Nullable CountDownLatch entered, @Nullable CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public String name() {
            return "TestLogHandler";
        }

        @Override
        public void handle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, CharSequence msg, @Nullable Throwable t) {
            if (entered != null) {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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

        @Override
        public void shutdown() {
            shutdownCount.incrementAndGet();
        }

        @Override
        public boolean isEnabled(LogLevel level) {
            return true;
        }

        @Override
        public boolean isLocationNeeded() {
            return false;
        }
    }
}
