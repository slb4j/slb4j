package org.slb4j.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.LoggingConfiguration;
import org.slb4j.filter.CombinedFilter;
import org.slb4j.filter.LogLevelFilter;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.FileHandler;
import org.slb4j.handler.RotatingFileHandler;
import org.slb4j.layout.PatternLayout;
import org.slb4j.layout.SimpleLayout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
                ),
                new TestCase("Basic Console", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = %d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """,
                        config -> {
                            LogHandler handler = config.getHandler("console");
                            assertNotNull(handler);
                            assertInstanceOf(ConsoleHandler.class, handler);
                            assertEquals("STDOUT", handler.name());
                            PatternLayout layout = (PatternLayout) ((ConsoleHandler) handler).getLayout();
                            assertEquals(PatternLayout.parseLog4jPattern("%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n").getText(), layout.getText());

                            LogFilter rootFilter = config.getRootFilter();
                            assertInstanceOf(LogLevelFilter.class, rootFilter);
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.DEBUG));
                        }
                ),
                new TestCase("Basic File", """
                        appender.file.type = File
                        appender.file.name = File
                        appender.file.fileName = build/test.log
                        appender.file.layout.type = PatternLayout
                        appender.file.layout.pattern = %d %p %C{1.} [%t] %m%n
                        rootLogger.level = info
                        rootLogger.appenderRef.file.ref = File
                        """,
                        config -> {
                            LogHandler handler = config.getHandler("file");
                            assertNotNull(handler);
                            assertInstanceOf(FileHandler.class, handler);
                            assertEquals("File", handler.name());
                            assertEquals(Path.of("build/test.log").toAbsolutePath(), ((FileHandler) handler).getPath().toAbsolutePath());

                            LogFilter rootFilter = config.getRootFilter();
                            assertInstanceOf(LogLevelFilter.class, rootFilter);
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.INFO));
                            assertTrue(!rootFilter.isLevelEnabled(LogLevel.DEBUG));
                        }
                ),
                new TestCase("Rolling File", """
                        appender.rolling.type = RollingFile
                        appender.rolling.name = RollingFile
                        appender.rolling.fileName = build/rolling.log
                        appender.rolling.filePattern = build/rolling-%d{MM-dd-yy-HH-mm-ss}-%i.log.gz
                        appender.rolling.layout.type = PatternLayout
                        appender.rolling.layout.pattern = %d %p %C{1.} [%t] %m%n
                        appender.rolling.policies.type = Policies
                        appender.rolling.policies.size.type = SizeBasedTriggeringPolicy
                        appender.rolling.policies.size.size = 100MB
                        appender.rolling.strategy.max = 5
                        rootLogger.level = info
                        rootLogger.appenderRef.rolling.ref = RollingFile
                        """,
                        config -> {
                            LogHandler handler = config.getHandler("rolling");
                            assertNotNull(handler);
                            assertInstanceOf(RotatingFileHandler.class, handler);
                            RotatingFileHandler rfh = (RotatingFileHandler) handler;
                            assertEquals("RollingFile", rfh.name());
                            assertEquals(Path.of("build/rolling.log").toAbsolutePath(), rfh.getPath().toAbsolutePath());
                            assertEquals("build/rolling-%d{MM-dd-yy-HH-mm-ss}-%i.log.gz", rfh.getFilePattern());
                            assertEquals(100L * 1024 * 1024, rfh.getMaxFileSize());
                            assertEquals(5, rfh.getMaxBackupIndex());
                        }
                ),
                new TestCase("Multiple Appenders", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.file.type = File
                        appender.file.name = FILE
                        appender.file.fileName = build/test.log
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        rootLogger.appenderRef.file.ref = FILE
                        """,
                        config -> {
                            assertEquals(2, config.getHandlers().size());
                            assertNotNull(config.getHandler("console"));
                            assertNotNull(config.getHandler("file"));
                            assertEquals(LogLevelFilter.class, config.getRootFilter().getClass());
                            assertTrue(config.getRootFilter().isLevelEnabled(LogLevel.DEBUG));
                        }
                ),
                new TestCase("Logger levels", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        logger.app.name = org.apache.logging.log4j.test1
                        logger.app.level = debug
                        logger.app.additivity = false
                        logger.app.appenderRef.stdout.ref = STDOUT
                        rootLogger.level = info
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """,
                        config -> {
                            LogHandler console = config.getHandlers().get("console");
                            assertNotNull(console);
                            LogFilter appFilter = console.getFilter();
                            assertNotNull(appFilter);
                            assertTrue(appFilter.isLevelEnabled(LogLevel.DEBUG));

                            LogFilter rootFilter = config.getRootFilter();
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.INFO));
                            assertTrue(!rootFilter.isLevelEnabled(LogLevel.DEBUG));
                        }
                ),
                new TestCase("Baeldung - 6. Syntax of the log4j2.properties", """
                        # The root logger with appender name
                        rootLogger = DEBUG, STDOUT
                        
                        # Assign STDOUT a valid appender & define its layout 
                        appender.console.name = STDOUT
                        appender.console.type = Console
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = %msg%n
                        """,
                        config -> {
                            LogHandler handler = config.getHandler("console");
                            assertNotNull(handler);
                            assertEquals("STDOUT", handler.name());
                            assertTrue(config.getRootFilter().isLevelEnabled(LogLevel.DEBUG));
                        }
                ),
                new TestCase("Baeldung - 7.2 Console Logging", """
                        # Root Logger
                        rootLogger=DEBUG, STDOUT
                        
                        # Direct log messages to stdout
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = [%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %C{1} - %msg%n
                        """,
                        config -> {
                            LogHandler handler = config.getHandler("console");
                            assertNotNull(handler);
                            assertTrue(config.getRootFilter().isLevelEnabled(LogLevel.DEBUG));
                        }
                ),
                new TestCase("Baeldung - 7.3. Multiple Destinations", """
                        # Root Logger
                        rootLogger=INFO, STDOUT, LOGFILE
                        
                        # Direct log messages to STDOUT
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = [%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %C{1} - %msg%n
                        
                        # Direct to a file
                        appender.file.type = File
                        appender.file.name = LOGFILE
                        appender.file.fileName = build/tmp/baeldung/logs/log4j2.log
                        appender.file.layout.type = PatternLayout
                        appender.file.layout.pattern = [%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %C{1} - %msg%n
                        appender.file.filter.threshold.type = ThresholdFilter
                        appender.file.filter.threshold.level = info
                        """,
                        config -> {
                            LogHandler fileHandler = config.getHandler("file");
                            assertNotNull(fileHandler.getFilter());
                            assertTrue(fileHandler.getFilter().isLevelEnabled(LogLevel.INFO));
                        }
                ),
                new TestCase("JSON Layout", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = JsonLayout
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """,
                        config -> {
                            LogHandler handler = config.getHandler("console");
                            assertNotNull(handler);
                            assertInstanceOf(ConsoleHandler.class, handler);
                            assertEquals("JsonLayout", ((ConsoleHandler) handler).getLayout().getType());
                        }
                ),
                new TestCase("Custom Filter", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.filter.threshold.type = ThresholdFilter
                        appender.console.filter.threshold.level = debug
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """,
                        config -> {
                            LogHandler handler = config.getHandler("console");
                            assertNotNull(handler.getFilter());
                            assertTrue(handler.getFilter().isLevelEnabled(LogLevel.DEBUG));
                        }
                ),
                new TestCase("Simple Layout", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = SimpleLayout
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """,
                        config -> {
                            LogHandler handler = config.getHandler("console");
                            assertInstanceOf(SimpleLayout.class, ((ConsoleHandler) handler).getLayout());
                        }
                ),
                new TestCase("Syslog Appender", """
                        appender.syslog.type = Syslog
                        appender.syslog.name = Syslog
                        appender.syslog.host = localhost
                        appender.syslog.port = 514
                        appender.syslog.protocol = UDP
                        rootLogger.level = debug
                        rootLogger.appenderRef.syslog.ref = Syslog
                        """,
                        config -> {
                            // Currently unsupported, should not crash and maybe return no handler or a default one
                        }
                ),
                new TestCase("Complex Policies", """
                        appender.rolling.type = RollingFile
                        appender.rolling.name = RollingFile
                        appender.rolling.fileName = build/rolling.log
                        appender.rolling.filePattern = build/rolling-%d{MM-dd-yy-HH-mm-ss}-%i.log.gz
                        appender.rolling.policies.type = Policies
                        appender.rolling.policies.time.type = TimeBasedTriggeringPolicy
                        appender.rolling.policies.time.interval = 2
                        appender.rolling.policies.time.modulate = true
                        appender.rolling.policies.size.type = SizeBasedTriggeringPolicy
                        appender.rolling.policies.size.size = 100MB
                        appender.rolling.strategy.type = DefaultRolloverStrategy
                        appender.rolling.strategy.max = 5
                        rootLogger.level = info
                        rootLogger.appenderRef.rolling.ref = RollingFile
                        """,
                        config -> {
                            LogHandler handler = config.getHandler("rolling");
                            assertNotNull(handler);
                            assertInstanceOf(RotatingFileHandler.class, handler);
                        }
                )
        );
    }
}
