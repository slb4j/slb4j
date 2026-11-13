package org.slb4j.layout;

import org.junit.jupiter.api.Test;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLayoutTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void testJsonOutputFormat() throws IOException {
        JsonLayout layout = new JsonLayout(true, true, true, 1024, "...");
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L; // 2024-01-31 04:00:00 UTC

        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.INFO, "testMarker", null, null,
                "test message with \"quotes\"", null, ConsoleCode.empty());

        String output = sb.toString();
        JsonNode json = parseJson(output);
        assertTrue(output.contains("\"epochSecond\":1706673600"), "output: " + output);
        assertTrue(output.contains("\"level\":\"INFO\""), "output: " + output);
        assertEquals("testLogger", json.get("loggerName").asString());
        assertTrue(output.contains("\"message\":\"test message with \\\"quotes\\\"\""), "output: " + output);
        assertTrue(output.contains("\"marker\":\"testMarker\""), "output: " + output);
        assertTrue(output.startsWith("{\"thread\":") && output.endsWith("}\n"), "output: " + output);
    }

    @Test
    void testJsonEncoding() throws IOException {
        String specialCharacters = "\"\\/\b\f\n\r\t\u0001\u001f";
        JsonLayout layout = new JsonLayout(true, true, true, 1024, "...");
        StringBuilder sb = new StringBuilder();

        MDC mdc = new MDC() {
            @Override
            public String get(String key) {
                return specialCharacters;
            }

            @Override
            public Map<String, String> get() {
                return Collections.singletonMap(specialCharacters, specialCharacters);
            }
        };

        Location location = new Location() {
            @Override
            public String getClassName() { return specialCharacters; }
            @Override
            public String getMethodName() { return specialCharacters; }
            @Override
            public int getLineNumber() { return 123; }
            @Override
            public String getFileName() { return specialCharacters; }
        };

        Thread currentThread = Thread.currentThread();
        String originalThreadName = currentThread.getName();
        try {
            currentThread.setName(specialCharacters);
            layout.formatLogEntry(sb, 1706673600000L, specialCharacters, LogLevel.INFO, specialCharacters,
                    mdc, location, specialCharacters, null, ConsoleCode.empty());
        } finally {
            currentThread.setName(originalThreadName);
        }

        JsonNode json = parseJson(sb.toString());
        assertEquals(specialCharacters, json.get("thread").asString());
        assertEquals(specialCharacters, json.get("loggerName").asString());
        assertEquals(specialCharacters, json.get("marker").asString());
        assertEquals(specialCharacters, json.get("message").asString());
        assertEquals(specialCharacters, json.get("contextMap").get(specialCharacters).asString());
        assertEquals(specialCharacters, json.get("location").get("class").asString());
        assertEquals(specialCharacters, json.get("location").get("method").asString());
        assertEquals(specialCharacters, json.get("location").get("file").asString());
    }

    @Test
    void testJsonEncodingWithTruncatedStringSuffix() throws IOException {
        JsonLayout layout = new JsonLayout(false, false, false, 1, "\"\n");
        StringBuilder sb = new StringBuilder();

        layout.formatLogEntry(sb, 0, "logger", LogLevel.INFO, null, null, null,
                "message", null, ConsoleCode.empty());

        JsonNode json = parseJson(sb.toString());
        assertEquals("l\"\n", json.get("loggerName").asString());
        assertEquals("m\"\n", json.get("message").asString());
    }

    @Test
    void testJsonOutputFormatWithMdc() throws IOException {
        JsonLayout layout = new JsonLayout(true, true, true, 1024, "...");
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L;

        MDC mdc = new MDC() {
            @Override
            public String get(String key) {
                return "value1";
            }

            @Override
            public Map<String, String> get() {
                return Collections.singletonMap("key1", "value1");
            }
        };

        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.INFO, null, mdc, null, "msg", null,
                ConsoleCode.empty());

        JsonNode json = parseJson(sb.toString());
        assertEquals("value1", json.get("contextMap").get("key1").asString());
    }

    @Test
    void testJsonOutputFormatWithLocation() throws IOException {
        JsonLayout layout = new JsonLayout(true, true, true, 1024, "...");
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L;

        Location location = new Location() {
            @Override
            public String getClassName() { return "com.example.Test"; }
            @Override
            public String getMethodName() { return "testMethod"; }
            @Override
            public int getLineNumber() { return 123; }
            @Override
            public String getFileName() { return "Test.java"; }
        };

        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.INFO, null, null, location, "msg", null,
                ConsoleCode.empty());

        JsonNode json = parseJson(sb.toString());
        assertEquals("com.example.Test", json.get("location").get("class").asString());
        assertEquals("testMethod", json.get("location").get("method").asString());
        assertEquals("Test.java", json.get("location").get("file").asString());
        assertEquals(123, json.get("location").get("line").asInt());
    }

    @Test
    void testJsonOutputFormatWithException() throws IOException {
        JsonLayout layout = new JsonLayout(true, true, true, 1024, "...");
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L;

        Throwable t = new RuntimeException("test exception");

        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.ERROR, null, null, null, "msg", t,
                ConsoleCode.empty());

        JsonNode thrown = parseJson(sb.toString()).get("thrown");
        assertTrue(thrown.isObject());
        assertEquals("test exception", thrown.get("message").asString());
        assertTrue(thrown.get("stacktrace").asString().startsWith("java.lang.RuntimeException: test exception"));
    }

    private static JsonNode parseJson(String output) throws IOException {
        return JSON.readTree(output);
    }
}
