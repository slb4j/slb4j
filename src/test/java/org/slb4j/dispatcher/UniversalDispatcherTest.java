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
        assertEquals(LogLevel.DEBUG, newFilter.getLevel());

        dispatcher.setLevel("com.example", LogLevel.TRACE);
        assertEquals(LogLevel.TRACE, dispatcher.getLevel("com.example"));
        assertEquals(LogLevel.TRACE, newFilter.getLevel("com.example"));

        // With root level DEBUG, TRACE is globally disabled
        assertFalse(dispatcher.isLevelEnabled(LogLevel.TRACE));

        // isEnabled checks both root level and logger specific level
        // In LoggerNamePrefixFilter.java:
        // return isLevelEnabled(logLevel) && logLevel.ordinal() >= getLevel(loggerName).ordinal();
        // Since isLevelEnabled(TRACE) is false, isEnabled will be false even if logger level is TRACE.
        assertFalse(dispatcher.isEnabled("com.example", LogLevel.TRACE, null));

        assertTrue(dispatcher.isLevelEnabled(LogLevel.DEBUG));
        assertTrue(dispatcher.isEnabled("com.example", LogLevel.DEBUG, null));
    }

    private static class TestLogHandler implements LogHandler {
        private LogFilter filter = LogFilter.allPass();

        @Override
        public String name() {
            return "TestLogHandler";
        }

        @Override
        public void handle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, String msg, @Nullable Throwable t) {
            // No-op
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
            // nothing to do
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
