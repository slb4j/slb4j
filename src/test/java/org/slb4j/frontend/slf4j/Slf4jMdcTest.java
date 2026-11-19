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
package org.slb4j.frontend.slf4j;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.layout.PatternLayout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Slf4jMdcTest {

    @Test
    void storesAndClearsContext() {
        try {
            MDC.put("userId", "benchUser");
            assertEquals("benchUser", MDC.get("userId"));
            assertEquals("benchUser", MDC.getCopyOfContextMap().get("userId"));
            MDC.remove("userId");
            assertNull(MDC.get("userId"));
            MDC.put("userId", "otherUser");
            MDC.clear();
            assertNull(MDC.get("userId"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void keepsContextChangesLocalToEachThread() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {}).get();
            MDC.put("userId", "benchUser");
            assertNull(executor.submit(() -> MDC.get("userId")).get());
            assertEquals("worker", executor.submit(() -> {
                MDC.put("userId", "worker");
                return MDC.get("userId");
            }).get());
            assertEquals("benchUser", MDC.get("userId"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void childThreadsInheritAnIndependentContextCopy() throws Exception {
        try {
            MDC.put("userId", "benchUser");
            try (var executor = Executors.newSingleThreadExecutor()) {
                assertEquals("benchUser", executor.submit(() -> {
                    String inherited = MDC.get("userId");
                    MDC.put("userId", "childUser");
                    return inherited;
                }).get());
            }
            assertEquals("benchUser", MDC.get("userId"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void contextMapsCanBeCopiedAndRestored() {
        try {
            MDC.put("userId", "benchUser");
            Map<String, String> copy = MDC.getCopyOfContextMap();
            copy.put("userId", "modifiedCopy");
            assertEquals("benchUser", MDC.get("userId"));
            MDC.setContextMap(copy);
            copy.put("userId", "changedAgain");
            assertEquals("modifiedCopy", MDC.get("userId"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void detailedLayoutRendersAndRemovesSlf4jContext() {
        Logger logger = LoggerFactory.getLogger(Slf4jMdcTest.class);
        UniversalDispatcher dispatcher = UniversalDispatcher.getInstance();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(output, false, StandardCharsets.UTF_8)) {
            ConsoleHandler handler = new ConsoleHandler("mdc-test", stream, false);
            handler.setLayout(PatternLayout.LAYOUT_INSTANCE_DETAILED);
            dispatcher.addLogHandler(handler);
            try {
                MDC.put("userId", "benchUser");
                logger.info("with context");
                MDC.clear();
                logger.info("without context");
            } finally {
                MDC.clear();
                dispatcher.removeLogHandler(handler);
            }
        }
        String[] lines = output.toString(StandardCharsets.UTF_8).split("\\R");
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("userId=benchUser"));
        assertTrue(lines[0].contains("with context"));
        assertFalse(lines[1].contains("benchUser"));
        assertTrue(lines[1].contains("without context"));
    }
}