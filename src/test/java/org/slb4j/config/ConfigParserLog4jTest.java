package org.slb4j.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slb4j.LayoutConfigurable;
import org.slb4j.LoggingConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigParserLog4jTest {

    record TestCase(String name, String properties) {
        @Override
        public String toString() {
            return name;
        }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void testParse(TestCase testCase) throws IOException {
        Properties props = new Properties();
        props.load(new ByteArrayInputStream(testCase.properties().getBytes(StandardCharsets.UTF_8)));

        LoggingConfiguration result = new ConfigParserLog4j().parse(props);

        assertNotNull(result, "Result should not be null for " + testCase.name());

        assertTrue(!result.getHandlers().isEmpty(), "Handlers should be parsed for " + testCase.name());
        // Basic verification:  that at least some handlers have a layout
        if (testCase.properties().contains("layout")) {
            assertTrue(result.getHandlers().values().stream()
                    .filter(LayoutConfigurable.class::isInstance)
                    .anyMatch(h -> ((LayoutConfigurable) h).getLayout() != null), "Layout should be present for " + testCase.name());
        }
        // Basic verification:  that at least some handlers have a filter
        if (testCase.properties().contains("filter") || testCase.properties().contains("filters")) {
            assertTrue(result.getHandlers().values().stream()
                    .anyMatch(h -> h.getFilter() != null), "Filter should be present for " + testCase.name());
        }
    }

    static Stream<TestCase> testCases() {
        return Stream.of(
                new TestCase("Single appender PatternLayout", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = %d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n
                        """),
                new TestCase("Multiple appenders different layouts", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = %d %-5p [%t] %C{2} (%F:%L) - %m%n
                        
                        appender.file.type = File
                        appender.file.name = File
                        appender.file.fileName = build/logs/app.log
                        appender.file.layout.type = SimpleLayout
                        """),
                new TestCase("Appender with filter", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.filter.type = ThresholdFilter
                        appender.console.filter.level = info
                        """),
                new TestCase("Appender with compound filter", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.filter.type = CompoundFilter
                        appender.console.filter.1.type = ThresholdFilter
                        appender.console.filter.1.level = info
                        appender.console.filter.2.type = MarkerFilter
                        appender.console.filter.2.marker = TEST
                        appender.console.filter.2.onMatch = ACCEPT
                        appender.console.filter.2.onMismatch = DENY
                        """)
        );
    }
}
