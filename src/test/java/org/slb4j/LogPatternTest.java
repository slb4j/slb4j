package org.slb4j;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogPatternTest {

    private static final LocationResolver LOC = () -> new Location() {
        @Override
        public String getClassName() {
            return "com.example.service.OrderService";
        }

        @Override
        public @Nullable String getMethodName() {
            return "processOrder";
        }

        @Override
        public int getLineNumber() {
            return 42;
        }

        @Override
        public @Nullable String getFileName() {
            return "OrderService.java";
        }
    };

    @ParameterizedTest(name = "[{index}] pattern=\"{0}\"")
    @CsvSource(
            delimiter = '|',
            ignoreLeadingAndTrailingWhitespace = false,
            value = {
                    // Core patterns
                    "%msg%n|'Order 4711 processed\n'",
                    "%level %msg%n|'INFO Order 4711 processed\n'",
                    "%-5level %msg%n|'INFO  Order 4711 processed\n'",

                    // Timestamp patterns
                    "%d{yyyy-MM-dd HH:mm:ss.SSS} %msg%n|'2026-01-10 14:23:41.123 Order 4711 processed\n'",
                    "%d{HH:mm:ss.SSS} %msg%n|'14:23:41.123 Order 4711 processed\n'",

                    // Logger name patterning
                    "%logger %msg%n|'com.example.service.OrderService Order 4711 processed\n'",
                    "%logger{1} %msg%n|'OrderService Order 4711 processed\n'",
                    "%logger{2} %msg%n|'service.OrderService Order 4711 processed\n'",

                    // Location patterns
                    "%C|'com.example.service.OrderService'",
                    "%C{1}|'OrderService'",
                    "%M|'processOrder'",
                    "%L|'42'",
                    "%F|'OrderService.java'",

                    // Thread & context
                    "%t %msg%n|'main Order 4711 processed\n'",
                    "%X{userId} %msg%n|'alice Order 4711 processed\n'",
                    "%X{missing} %msg%n|' Order 4711 processed\n'",

                    // Marker
                    "%marker %msg%n|'AUDIT Order 4711 processed\n'",
                    "[%marker] %msg%n|'[AUDIT] Order 4711 processed\n'",

                    // Escaping & literals
                    "%% %msg%n|'% Order 4711 processed\n'",
                    "\"%msg\"%n|'\"Order 4711 processed\"\n'",

                    // Composite
                    "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{2} [%marker] %X{userId} - %msg%n"
                            + "|'2026-01-10 14:23:41.123 [main] INFO  service.OrderService [AUDIT] alice - Order 4711 processed\n'"
            }
    )
    void testPattern(String pattern, String expected) throws IOException {
        String threadName = Thread.currentThread().getName();
        String updatedExpected = expected.replace("main", threadName);
        LogPattern fmt = LogPattern.parse(pattern);

        Instant instant = LocalDateTime.of(2026, 1, 10, 14, 23, 41, 123_000_000)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        String loggerName = "com.example.service.OrderService";
        LogLevel level = LogLevel.INFO;
        String marker = "AUDIT";
        MDC mdc = new MDC() {
            Map<String, String> map = Map.of("userId", "alice", "requestId", "req-123");

            @Override
            public @Nullable String get(String key) {
                return map.get(key);
            }

            @Override
            public Stream<Map.Entry<String, String>> stream() {
                return map.entrySet().stream();
            }
        };
        Supplier<String> msg = () -> "Order 4711 processed";
        Throwable t = null;
        ConsoleCode consoleCodes = ConsoleCode.empty();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            fmt.formatLogEntry(out, instant, loggerName, level, marker, mdc, LOC, msg, t, consoleCodes);
        }

        String actual = baos.toString(StandardCharsets.UTF_8);

        assertEquals(updatedExpected, actual);
    }

    @Test
    void testPatternWithThrowable() throws IOException {
        String pattern = "%msg%n%ex";
        LogPattern fmt = LogPattern.parse(pattern);

        Instant instant = Instant.now();
        String loggerName = "test.Logger";
        LogLevel level = LogLevel.ERROR;
        Supplier<String> msg = () -> "An error occurred";
        Throwable t = new RuntimeException("Test exception");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            fmt.formatLogEntry(out, instant, loggerName, level, null, null, LOC, msg, t, null);
        }

        String actual = baos.toString(StandardCharsets.UTF_8);

        // Check if message and exception details are present
        String expectedStart = "An error occurred\njava.lang.RuntimeException: Test exception\n";
        assertEquals(expectedStart, actual.substring(0, expectedStart.length()));
        // Check if it contains some stack trace elements
        org.junit.jupiter.api.Assertions.assertTrue(actual.contains("at org.slb4j.LogPatternTest.testPatternWithThrowable"));
    }
}
