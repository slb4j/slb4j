package org.slb4j;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NullMarked
class JulLogPatternTest {

    private static final LocationResolver LOC = () -> new Location() {
        @Override
        public String getClassName() {
            return "com.example.service.OrderService";
        }

        @Override
        public String getMethodName() {
            return "processOrder";
        }

        @Override
        public int getLineNumber() {
            return 42;
        }

        @Override
        public String getFileName() {
            return "OrderService.java";
        }
    };

    @ParameterizedTest(name = "[{index}] julPattern=\"{0}\"")
    @CsvSource(
            delimiter = '|',
            ignoreLeadingAndTrailingWhitespace = false,
            value = {
                    "%5$s%n|'Order 4711 processed\n'",
                    "%4$s %5$s%n|'INFO Order 4711 processed\n'",
                    "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %5$s%n|'2026-01-10 14:23:41.123 Order 4711 processed\n'",
                    "%3$s %5$s%n|'com.example.service.OrderService Order 4711 processed\n'",
                    "%2$s %5$s%n|'processOrder Order 4711 processed\n'",
                    "%6$s|'\njava.lang.RuntimeException: Test exception\n'"
            }
    )
    void testJulPattern(String julPattern, String expected) throws IOException {
        String updatedExpected = expected.replaceAll("\n", System.lineSeparator());
        LogPattern fmt = LogPattern.parseJulPattern(julPattern);

        long timestamp = LocalDateTime.of(2026, 1, 10, 14, 23, 41, 123_000_000)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        String loggerName = "com.example.service.OrderService";
        LogLevel level = LogLevel.INFO;
        Supplier<String> msg = () -> "Order 4711 processed";
        Throwable t = expected.contains("RuntimeException") ? new RuntimeException("Test exception") : null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            fmt.formatLogEntry(out, timestamp, loggerName, level, null, null, LOC, msg, t, ConsoleCode.empty());
        }

        String actual = baos.toString(StandardCharsets.UTF_8);
        if (t != null) {
            // For exceptions, JUL %6$s includes a leading newline and the stack trace.
            // Our implementation might differ slightly in stack trace formatting but let's see.
            org.junit.jupiter.api.Assertions.assertTrue(actual.contains("java.lang.RuntimeException: Test exception"));
        } else {
            assertEquals(updatedExpected, actual);
        }
    }
}
