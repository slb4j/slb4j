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
package org.slb4j;

import org.junit.jupiter.api.Test;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.filter.LoggerNamePrefixFilter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SLB4JConfigurationTest {

    @Test
    void testGetSetConfiguration() {
        LoggingConfiguration initialConfig = SLB4J.getConfiguration();
        assertNotNull(initialConfig);

        LoggingConfiguration newConfig = LoggingConfiguration.defaultConfiguration();
        newConfig.setStatusLevel(LogLevel.ERROR);
        newConfig.setStatusName("test-status");
        
        // Custom filter to verify it's applied to the dispatcher
        LoggerNamePrefixFilter newFilter = new LoggerNamePrefixFilter("new");
        newFilter.setLevel(LogLevel.ERROR);
        newFilter.setLevel("new.logger", LogLevel.ERROR);
        newConfig.setLoggerFilter(newFilter);

        SLB4J.setConfiguration(newConfig);

        assertEquals(newConfig, SLB4J.getConfiguration());
        assertEquals(LogLevel.ERROR, SLB4J.getStatusLevel());
        assertEquals("test-status", SLB4J.getStatusName());
        
        // Verify dispatcher is updated
        UniversalDispatcher dispatcher = (UniversalDispatcher) SLB4J.getDispatcher();
        assertFalse(dispatcher.isEnabled("any.logger", LogLevel.INFO, null));
        assertFalse(dispatcher.isEnabled("new.logger", LogLevel.INFO, null));
        assertTrue(dispatcher.isEnabled("new.logger", LogLevel.ERROR, null));

        // Restore initial configuration
        SLB4J.setConfiguration(initialConfig);
        assertEquals(initialConfig, SLB4J.getConfiguration());
    }

    @Test
    void testHandlersUpdate() {
        LoggingConfiguration initialConfig = SLB4J.getConfiguration();
        
        LoggingConfiguration config = new LoggingConfiguration();
        TestHandler handler = new TestHandler("test");
        config.addHandler("test", handler);
        
        SLB4J.setConfiguration(config);
        
        LogDispatcher dispatcher = SLB4J.getDispatcher();
        assertTrue(dispatcher.getLogHandlers().contains(handler), "Dispatcher should contain our handler");
        assertEquals(1, dispatcher.getLogHandlers().size(), "Dispatcher should have exactly 1 handler, but had: " + dispatcher.getLogHandlers());
        
        // Clean up
        SLB4J.setConfiguration(initialConfig);
    }

    private static class TestHandler implements LogHandler {
        private final String name;
        private final List<String> messages = new ArrayList<>();
        private LogFilter filter = LogFilter.allPass();

        TestHandler(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void handle(long timestamp, String loggerName, LogLevel level, @Nullable String marker, @Nullable MDC mdc, @Nullable Location location, CharSequence message, @Nullable Throwable throwable) {
            messages.add(message.toString());
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
        public boolean isLocationNeeded() {
            return false;
        }
    }
}
