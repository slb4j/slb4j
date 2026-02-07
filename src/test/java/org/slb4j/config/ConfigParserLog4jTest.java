package org.slb4j.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slb4j.LayoutConfigurable;
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.LoggingConfiguration;
import org.slb4j.filter.CombinedFilter;
import org.slb4j.filter.LogLevelFilter;
import org.slb4j.filter.MarkerFilter;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.FileHandler;
import org.slb4j.layout.PatternLayout;
import org.slb4j.layout.SimpleLayout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigParserLog4jTest {

    record TestCase(String name, String properties, Consumer<LoggingConfiguration> validate) {
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

        if (testCase.validate() != null) {
            testCase.validate().accept(result);
        }

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
                        """,
                        config -> {
                            LogHandler handler = config.getHandlers().get("console");
                            assertNotNull(handler, "Handler 'console' should be present");
                            assertEquals("STDOUT", handler.name(), "Handler 'console' should have expected name");
                            assertInstanceOf(ConsoleHandler.class, handler, "Handler 'console' should be ConsoleHandler");
                            ConsoleHandler consoleHandler = (ConsoleHandler) handler;
                            assertSame(System.out, consoleHandler.getOut(), "Handler 'console' should use System.out as output");
                            assertInstanceOf(PatternLayout.class, consoleHandler.getLayout(), "Handler 'console' layout should be PatternLayout");
                            PatternLayout layout = (PatternLayout) consoleHandler.getLayout();
                            assertEquals("%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n", layout.getText(), "Handler 'console' layout should match expected pattern");
                        }
                ),
                new TestCase("Multiple appenders different layouts", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = %d %-5p [%t] %C{2} (%F:%L) - %m%n
                        
                        appender.file.type = File
                        appender.file.name = File
                        appender.file.fileName = build/logs/app.log
                        appender.file.layout.type = SimpleLayout
                        """,
                        config -> {
                            LogHandler consoleHandler = config.getHandlers().get("console");
                            assertNotNull(consoleHandler, "Handler 'console' should be present");
                            assertEquals("STDOUT", consoleHandler.name(), "Handler 'console' should have expected name");
                            assertInstanceOf(ConsoleHandler.class, consoleHandler, "Handler 'console' should be ConsoleHandler");
                            assertSame(System.out, ((ConsoleHandler) consoleHandler).getOut(), "Handler 'console' should use System.out as output");
                            assertInstanceOf(PatternLayout.class, ((ConsoleHandler) consoleHandler).getLayout(), "Handler 'console' layout should be PatternLayout");
                            assertEquals("%d %-5p [%t] %C{2} (%F:%L) - %m%n", ((PatternLayout) ((ConsoleHandler) consoleHandler).getLayout()).getText(), "Handler 'console' layout should match expected pattern");

                            LogHandler fileHandler = config.getHandlers().get("file");
                            assertNotNull(fileHandler, "Handler 'file' should be present");
                            assertEquals("File", fileHandler.name(), "Handler 'file' should have expected name");
                            assertInstanceOf(FileHandler.class, fileHandler, "Handler 'file' should be FileHandler");
                            assertEquals("build/logs/app.log", ((FileHandler) fileHandler).getPath().toString(), "Handler 'file' should use expected file path");
                            assertInstanceOf(SimpleLayout.class, ((FileHandler) fileHandler).getLayout(), "Handler 'file' layout should be SimpleLayout");
                        }
                ),
                new TestCase("Appender with filter", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.filter.type = ThresholdFilter
                        appender.console.filter.level = info
                        """,
                        config -> {
                            LogHandler handler = config.getHandlers().get("console");
                            assertNotNull(handler);
                            assertInstanceOf(ConsoleHandler.class, handler);
                            LogFilter filter = handler.getFilter();
                            assertNotNull(filter, "Filter should be present on handler 'console'");
                            assertInstanceOf(LogLevelFilter.class, filter);
                            assertTrue(filter.isLevelEnabled(LogLevel.INFO));
                            assertTrue(!filter.isLevelEnabled(LogLevel.DEBUG));
                        }
                ),
                new TestCase("Appender with compound filter (manual combine)", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.filter.1.type = ThresholdFilter
                        appender.console.filter.1.level = info
                        appender.console.filter.2.type = MarkerFilter
                        appender.console.filter.2.marker = TEST
                        appender.console.filter.2.onMatch = ACCEPT
                        appender.console.filter.2.onMismatch = DENY
                        """,
                        config -> {
                            LogHandler handler = config.getHandlers().get("console");
                            assertNotNull(handler);
                            LogFilter filter = handler.getFilter();
                            assertNotNull(filter);
                            // It should be a CombinedFilter because multiple filters were added
                            assertInstanceOf(CombinedFilter.class, filter);
                        }
                ),
                new TestCase("rootLogger", """
                        status = WARN
                        
                        appender.console.type = Console
                        appender.console.name = Console
                        appender.console.target = SYSTEM_OUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = %d{yy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n
                        
                        rootLogger.level = info
                        rootLogger.appenderRef.console.ref = Console
                        """,
                        config -> {
                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter);
                            assertInstanceOf(LogLevelFilter.class, rootFilter);
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.INFO));
                            assertTrue(!rootFilter.isLevelEnabled(LogLevel.DEBUG));

                            LogHandler consoleHandler = config.getHandlers().get("console");
                            assertNotNull(consoleHandler);
                        }
                )
        );
    }
}
