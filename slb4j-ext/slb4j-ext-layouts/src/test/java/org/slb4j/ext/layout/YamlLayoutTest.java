package org.slb4j.ext.layout;

import org.junit.jupiter.api.Test;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.MDC;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlLayoutTest {

    @Test
    void testYamlOutputFormat() throws IOException {
        YamlLayout layout = new YamlLayout(ZoneOffset.UTC);
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L; // 2024-01-31 04:00:00 UTC
        
        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.INFO, "testMarker", null, null, "test message with \"quotes\"", null, ConsoleCode.empty());
        
        String output = sb.toString();
        assertTrue(output.contains("---\n"));
        assertTrue(output.matches("(?s).*timestamp: \"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(Z|[+-]\\d{2}:\\d{2})\".*"));
        assertTrue(output.contains("level: \"INFO\""));
        assertTrue(output.contains("logger: \"testLogger\""));
        assertTrue(output.contains("message: \"test message with \\\"quotes\\\"\""));
        assertTrue(output.contains("marker: \"testMarker\""));
    }

    @Test
    void testYamlOutputFormatWithMdc() throws IOException {
        YamlLayout layout = new YamlLayout(ZoneOffset.UTC);
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
        
        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.INFO, null, mdc, null, "msg", null, ConsoleCode.empty());
        
        String output = sb.toString();
        assertTrue(output.contains("mdc:\n"));
        assertTrue(output.contains("  key1: \"value1\""));
    }

    @Test
    void testYamlOutputFormatWithLocation() throws IOException {
        YamlLayout layout = new YamlLayout(ZoneOffset.UTC);
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
        
        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.INFO, null, null, location, "msg", null, ConsoleCode.empty());
        
        String output = sb.toString();
        assertTrue(output.contains("location:\n"));
        assertTrue(output.contains("  class: \"com.example.Test\""));
        assertTrue(output.contains("  method: \"testMethod\""));
        assertTrue(output.contains("  file: \"Test.java\""));
        assertTrue(output.contains("  line: 123"));
    }

    @Test
    void testYamlOutputFormatWithException() throws IOException {
        YamlLayout layout = new YamlLayout(ZoneOffset.UTC);
        StringBuilder sb = new StringBuilder();
        long timestamp = 1706673600000L;
        
        Throwable t = new RuntimeException("test exception");
        
        layout.formatLogEntry(sb, timestamp, "testLogger", LogLevel.ERROR, null, null, null, "msg", t, ConsoleCode.empty());
        
        String output = sb.toString();
        assertTrue(output.contains("exception: \"java.lang.RuntimeException: test exception"));
        assertTrue(output.contains("\\n\\tat "));
    }
}
