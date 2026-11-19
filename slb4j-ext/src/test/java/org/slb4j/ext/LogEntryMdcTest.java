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
package org.slb4j.ext;

import org.junit.jupiter.api.Test;
import org.slb4j.LogLevel;
import org.slb4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogEntryMdcTest {

    @Test
    void bufferedEntriesRetainTheContextAtLoggingTime() throws Exception {
        ThreadLocal<Map<String, String>> context = ThreadLocal.withInitial(HashMap::new);
        MDC mdc = new MDC() {
            @Override
            public String get(String key) {
                return context.get().get(key);
            }

            @Override
            public Map<String, String> get() {
                return context.get();
            }
        };
        LogBuffer buffer = new LogBuffer("test");
        context.get().put("userId", "benchUser");
        context.get().put("optional", null);
        buffer.handle(0, "test", LogLevel.INFO, null, mdc, null, "first", null);
        context.get().put("userId", "nextUser");
        buffer.handle(0, "test", LogLevel.INFO, null, mdc, null, "second", null);
        context.remove();

        assertEquals("benchUser", buffer.get(0).mdc().get("userId"));
        assertTrue(buffer.get(0).mdc().get().containsKey("optional"));
        assertNull(buffer.get(0).mdc().get("optional"));
        assertEquals("nextUser", buffer.get(1).mdc().get("userId"));
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertEquals("benchUser", executor.submit(() -> buffer.get(0).mdc().get("userId")).get());
        }
        assertThrows(UnsupportedOperationException.class, () -> buffer.get(0).mdc().get().clear());
    }

    @Test
    void entryWithoutContextRemainsWithoutContext() {
        LogEntry entry = LogEntry.of(0, "test", LogLevel.INFO, null, MDC.createMdc(), null, "message", null);
        assertTrue(entry.mdc().get().isEmpty());
    }
}