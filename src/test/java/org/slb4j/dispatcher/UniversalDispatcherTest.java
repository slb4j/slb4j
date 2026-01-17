package org.slb4j.dispatcher;

import org.junit.jupiter.api.Test;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import org.slb4j.LocationResolver;
import org.slb4j.LogFilter;

import java.time.Instant;
import java.util.Collection;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class UniversalDispatcherTest {

    @Test
    void testAddAndRemoveLogHandler() {
        UniversalDispatcher dispatcher = new UniversalDispatcher();
        LogHandler handler = new TestLogHandler();

        dispatcher.addLogHandler(handler);
        Collection<LogHandler> handlers = dispatcher.getLogHandlers();
        assertTrue(handlers.contains(handler), "Handler should be present after adding");
        assertEquals(1, handlers.size());

        dispatcher.removeLogHandler(handler);
        handlers = dispatcher.getLogHandlers();
        assertFalse(handlers.contains(handler), "Handler should not be present after removal");
        assertEquals(0, handlers.size());
    }

    private static class TestLogHandler implements LogHandler {
        private LogFilter filter = LogFilter.allPass();

        @Override
        public String name() {
            return "TestLogHandler";
        }

        @Override
        public void handle(Instant instant, String loggerName, LogLevel lvl, String mrk, MDC mdc, LocationResolver locationResolver, Supplier<String> msg, Throwable t) {
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
        public boolean isEnabled(LogLevel level) {
            return true;
        }
    }
}
