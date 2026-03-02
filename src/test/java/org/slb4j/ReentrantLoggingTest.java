package org.slb4j;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.frontend.jcl.LoggerJcl;
import org.slb4j.frontend.jul.JulHandler;
import org.slb4j.frontend.log4j.LoggerLog4j;
import org.slb4j.frontend.slf4j.LoggerSlf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ReentrantLoggingTest {

    private static class ReentrantObject {
        private final Runnable logAction;

        public ReentrantObject(Runnable logAction) {
            this.logAction = logAction;
        }

        @Override
        public String toString() {
            logAction.run();
            return "ReentrantObject";
        }
    }

    private List<String> messages;

    @BeforeEach
    void setup() {
        messages = new ArrayList<>();
        UniversalDispatcher dispatcher = UniversalDispatcher.getInstance();
        dispatcher.clearLogHandlers();
        dispatcher.addLogHandler(new LogHandler() {
            private LogFilter filter = LogFilter.allPass();
            @Override public String name() { return "counting"; }
            @Override public void handle(long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, CharSequence msg, @Nullable Throwable t) {
                messages.add(msg.toString());
            }
            @Override public void setFilter(LogFilter filter) { this.filter = filter; }
            @Override public LogFilter getFilter() { return filter; }
            @Override public void shutdown() { /* nothing to do */}
            @Override public boolean isLocationNeeded() { return false; }
        });
        dispatcher.setRootLevel(LogLevel.INFO);
    }

    @Test
    void testLog4j2Reentry() {
        org.apache.logging.log4j.Logger logger = new LoggerLog4j("test-log4j2");
        ReentrantObject reentrant = new ReentrantObject(() -> logger.info("Inner log message"));
        
        logger.info("Outer log message: {}", reentrant);
        
        assertEquals(2, messages.size(), "Should have 2 messages, but got: " + messages);
        assertEquals("Inner log message", messages.get(0));
        assertTrue(messages.get(1).contains("Outer log message: ReentrantObject"));
    }

    @Test
    void testLog4j2LambdaReentry() {
        org.apache.logging.log4j.Logger logger = new LoggerLog4j("test-log4j2-lambda");
        ReentrantObject reentrant = new ReentrantObject(() -> logger.info("Inner log message"));
        
        logger.info(() -> "Outer log message: " + reentrant);
        
        assertEquals(2, messages.size(), "Should have 2 messages, but got: " + messages);
        assertEquals("Inner log message", messages.get(0));
        assertTrue(messages.get(1).contains("Outer log message: ReentrantObject"));
    }

    @Test
    void testSlf4jReentry() {
        org.slf4j.Logger logger = new LoggerSlf4j("test-slf4j");
        ReentrantObject reentrant = new ReentrantObject(() -> logger.info("Inner log message"));
        
        logger.info("Outer log message: {}", reentrant);
        
        assertEquals(2, messages.size(), "Should have 2 messages, but got: " + messages);
        assertEquals("Inner log message", messages.get(0));
        assertTrue(messages.get(1).contains("Outer log message: ReentrantObject"));
    }

    @Test
    void testSlf4jLambdaReentry() {
        org.slf4j.Logger logger = new LoggerSlf4j("test-slf4j-lambda");
        ReentrantObject reentrant = new ReentrantObject(() -> logger.info("Inner log message"));
        
        logger.atInfo().log(() -> "Outer log message: " + reentrant);
        
        assertEquals(2, messages.size(), "Should have 2 messages, but got: " + messages);
        assertEquals("Inner log message", messages.get(0));
        assertTrue(messages.get(1).contains("Outer log message: ReentrantObject"));
    }

    @Test
    void testJulReentry() {
        JulHandler julHandler = new JulHandler();
        Logger logger = Logger.getLogger("test-jul");
        logger.setUseParentHandlers(false);
        logger.addHandler(julHandler);
        
        ReentrantObject reentrant = new ReentrantObject(() -> logger.info("Inner log message"));
        
        logger.info("Outer log message: " + reentrant);
        
        assertEquals(2, messages.size(), "Should have 2 messages, but got: " + messages);
        assertEquals("Inner log message", messages.get(0));
        assertTrue(messages.get(1).contains("Outer log message: ReentrantObject"));
    }

    @Test
    void testJulLambdaReentry() {
        JulHandler julHandler = new JulHandler();
        Logger logger = Logger.getLogger("test-jul-lambda");
        logger.setUseParentHandlers(false);
        logger.addHandler(julHandler);
        
        ReentrantObject reentrant = new ReentrantObject(() -> logger.info("Inner log message"));
        
        logger.info(() -> "Outer log message: " + reentrant);
        
        assertEquals(2, messages.size(), "Should have 2 messages, but got: " + messages);
        assertEquals("Inner log message", messages.get(0));
        assertTrue(messages.get(1).contains("Outer log message: ReentrantObject"));
    }

    @Test
    void testJclReentry() {
        org.apache.commons.logging.Log logger = new LoggerJcl("test-jcl");
        ReentrantObject reentrant = new ReentrantObject(() -> logger.info("Inner log message"));
        
        logger.info("Outer log message: " + reentrant);
        
        assertEquals(2, messages.size(), "Should have 2 messages, but got: " + messages);
        assertEquals("Inner log message", messages.get(0));
        assertTrue(messages.get(1).contains("Outer log message: ReentrantObject"));
    }
}
