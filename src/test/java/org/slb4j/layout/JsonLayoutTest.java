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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLayoutTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MDC MDC_INSTANCE = MDC.createMdc();

    @Test
    void testJsonOutputFormat() throws IOException {
        JsonLayout layout = new JsonLayout(true, true, true, 1024, "...");
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L; // 2024-01-31 04:00:00 UTC

        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.INFO, "testMarker", MDC_INSTANCE, null,
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

        layout.formatLogEntry(sb, 0, "logger", LogLevel.INFO, null, MDC_INSTANCE, null,
                "message", null, ConsoleCode.empty());

        JsonNode json = parseJson(sb.toString());
        assertEquals("l\"\n", json.get("loggerName").asString());
        assertEquals("m\"\n", json.get("message").asString());
    }

    @Test
    void testJsonEncodingWithMalformedUnicode() throws IOException {
        JsonLayout layout = new JsonLayout(false, false, false, 1024, "...");
        StringBuilder sb = new StringBuilder();

        layout.formatLogEntry(sb, 0, "logger", LogLevel.INFO, null, MDC_INSTANCE, null,
                "a\uD800b\uDC00c\u2028\u2029", null, ConsoleCode.empty());

        JsonNode json = parseJson(sb.toString());
        assertEquals("a�b�c\u2028\u2029", json.get("message").asString());
    }

    @Test
    void testJsonEncodingDoesNotSplitSurrogatePairWhenTruncating() throws IOException {
        String emoji = "\uD83D\uDE00";

        JsonLayout truncatedLayout = new JsonLayout(false, false, false, 2, "...");
        StringBuilder truncated = new StringBuilder();
        truncatedLayout.formatLogEntry(truncated, 0, "logger", LogLevel.INFO, null, MDC_INSTANCE, null,
                "A" + emoji + "B", null, ConsoleCode.empty());
        assertEquals("A...", parseJson(truncated.toString()).get("message").asString());

        JsonLayout completeLayout = new JsonLayout(false, false, false, 2, "...");
        StringBuilder complete = new StringBuilder();
        completeLayout.formatLogEntry(complete, 0, "logger", LogLevel.INFO, null, MDC_INSTANCE, null,
                emoji, null, ConsoleCode.empty());
        assertEquals(emoji, parseJson(complete.toString()).get("message").asString());
    }

    @Test
    void testJsonLayoutRejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> new JsonLayout(false, false, false, -1, "..."));
        assertThrows(IllegalArgumentException.class,
                () -> new JsonLayout(false, false, false, JsonLayout.HARD_MAX_STRING_LENGTH + 1, "..."));
        assertThrows(IllegalArgumentException.class,
                () -> new JsonLayout(false, false, false, 1,
                        "x".repeat(JsonLayout.MAX_TRUNCATED_STRING_SUFFIX_LENGTH + 1)));
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

        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.INFO, null, MDC_INSTANCE, location, "msg", null,
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

        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.ERROR, null, MDC_INSTANCE, null, "msg", t,
                ConsoleCode.empty());

        JsonNode thrown = parseJson(sb.toString()).get("thrown");
        assertTrue(thrown.isObject());
        assertEquals("test exception", thrown.get("message").asString());
        assertTrue(thrown.get("stacktrace").asString().startsWith("java.lang.RuntimeException: test exception"));
    }

    @Test
    void testJsonOutputFormatWithNestedCauses() throws IOException {
        JsonLayout layout = new JsonLayout(false, false, true, JsonLayout.HARD_MAX_STRING_LENGTH, "...");
        Throwable cause = new IllegalArgumentException("cause \"message\"");
        Throwable throwable = new RuntimeException("outer", cause);
        throwable.addSuppressed(new IllegalStateException("suppressed\nmessage"));
        StringBuilder sb = new StringBuilder();

        layout.formatLogEntry(sb, 0, "logger", LogLevel.ERROR, null, MDC_INSTANCE, null,
                "message", throwable, ConsoleCode.empty());

        JsonNode thrown = parseJson(sb.toString()).get("thrown");
        assertTrue(thrown.isObject());
        assertTrue(thrown.get("stacktrace").asString().contains("Caused by: java.lang.IllegalArgumentException: cause \"message\""));
        assertTrue(thrown.get("stacktrace").asString().contains("Suppressed: java.lang.IllegalStateException: suppressed"));
    }

    @Test
    void testEveryOutputLineIsACompleteJsonEvent() throws IOException {
        JsonLayout layout = new JsonLayout(false, false, false, 1024, "...");
        StringBuilder sb = new StringBuilder();

        layout.formatLogEntry(sb, 0, "logger", LogLevel.INFO, null, MDC_INSTANCE, null,
                "first", null, ConsoleCode.empty());
        layout.formatLogEntry(sb, 1, "logger", LogLevel.INFO, null, MDC_INSTANCE, null,
                "second\nmessage", null, ConsoleCode.empty());

        String[] lines = sb.toString().split("\\R", -1);
        assertEquals(3, lines.length);
        assertEquals("", lines[2]);
        assertTrue(parseJson(lines[0]).isObject());
        assertTrue(parseJson(lines[1]).isObject());
    }

    private static JsonNode parseJson(String output) {
        return JSON.readTree(output);
    }
}
