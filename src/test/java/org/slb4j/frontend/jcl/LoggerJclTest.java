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
package org.slb4j.frontend.jcl;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.slb4j.Location;
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.dispatcher.UniversalDispatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggerJclTest {

    @Test
    void includesSlf4jMdcInLogEvents() {
        ThreadContext.put("userId", "benchUser");
        UniversalDispatcher dispatcher = UniversalDispatcher.getInstance();
        LogLevel originalRootLevel = dispatcher.getRootLevel();
        CapturingHandler handler = new CapturingHandler();
        dispatcher.setRootLevel(LogLevel.TRACE);
        dispatcher.addLogHandler(handler);
        try {
            new LoggerJcl("test-jcl").info("message");
            assertNotNull(handler.mdc);
            assertEquals("benchUser", handler.mdc.get("userId"));
            assertTrue(handler.message.toString().contains("message"));
        } finally {
            ThreadContext.removeStack();
            dispatcher.removeLogHandler(handler);
            dispatcher.setRootLevel(originalRootLevel);
        }
    }

    private static final class CapturingHandler implements LogHandler {
        private LogFilter filter = LogFilter.allPass();
        private MDC mdc;
        private CharSequence message = "";

        @Override
        public String name() {
            return "jcl-mdc-test";
        }

        @Override
        public void handle(long timestamp, String loggerName, LogLevel level, @Nullable String marker,
                           MDC mdc, @Nullable Location location, CharSequence message,
                           @Nullable Throwable throwable) {
            this.mdc = mdc;
            this.message = message;
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
