package org.slb4j.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.LoggingConfiguration;
import org.slb4j.filter.CombinedFilter;
import org.slb4j.filter.LogLevelFilter;
import org.slb4j.filter.LoggerNamePrefixFilter;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.FileHandler;
import org.slb4j.handler.RotatingFileHandler;
import org.slb4j.layout.PatternLayout;
import org.slb4j.layout.SimpleLayout;
import org.slb4j.support.Util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                            assertEquals("build/logs/app.log", Util.pathToNormalizedString(((FileHandler) fileHandler).getPath()), "Handler 'file' should use expected file path");
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
                            assertNotNull(handler, "Handler 'console' should be present");
                            assertInstanceOf(ConsoleHandler.class, handler, "Handler 'console' should be ConsoleHandler");
                            LogFilter filter = handler.getFilter();
                            assertNotNull(filter, "Filter should be present on handler 'console'");
                            assertInstanceOf(LogLevelFilter.class, filter, "Filter for 'console' should be LogLevelFilter");
                            assertTrue(filter.isLevelEnabled(LogLevel.INFO), "LogLevel INFO should be enabled");
                            assertFalse(filter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should not be enabled");
                        }
                ),
                new TestCase("Logger entries", """
                        logger.com_foo.name = com.foo
                        logger.com_foo.level = DEBUG
                        logger.com_bar.name = com.bar
                        logger.com_bar.level = ERROR
                        """,
                        config -> {
                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should not be null when loggers are defined");
                            assertInstanceOf(LogLevelFilter.class, rootFilter);
                            assertEquals(LogLevelFilter.pass(LogLevel.ERROR), rootFilter, "Root filter show be pass(ERROR)");

                            LoggerNamePrefixFilter loggerFilter = config.getLoggerFilter();
                            assertNotNull(loggerFilter, "Logger filter should not be null when loggers are defined");
                            assertEquals(LogLevel.DEBUG, loggerFilter.getLevel("com.foo"), "Level for 'com.foo' should be DEBUG");
                            assertEquals(LogLevel.ERROR, loggerFilter.getLevel("com.bar"), "Level for 'com.bar' should be ERROR");
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
                            assertNotNull(handler, "Handler 'console' should be present");
                            LogFilter filter = handler.getFilter();
                            assertNotNull(filter, "Filter should be present on handler 'console'");
                            assertInstanceOf(CombinedFilter.class, filter, "Filter for 'console' should be CombinedFilter");
                            // Check that it contains both filters by checking behavior
                            assertTrue(filter.isLevelEnabled(LogLevel.INFO), "LogLevel INFO should be enabled");
                            assertFalse(filter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should not be enabled");
                            // MarkerFilter behavior
                            assertTrue(filter.isEnabled("any", LogLevel.INFO, "TEST"), "Should be enabled for marker TEST");
                            assertFalse(filter.isEnabled("any", LogLevel.INFO, "OTHER"), "Should not be enabled for marker OTHER");
                        }
                ),
                new TestCase("Root Logger", """
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
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.INFO), "LogLevel INFO should be enabled in root filter");
                            assertFalse(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should not be enabled in root filter");

                            LogHandler consoleHandler = config.getHandlers().get("console");
                            assertNotNull(consoleHandler, "Handler 'console' should be present");
                            assertEquals("Console", consoleHandler.name(), "Handler 'console' name should be 'Console'");
                            assertInstanceOf(ConsoleHandler.class, consoleHandler, "Handler 'console' should be ConsoleHandler");
                            ConsoleHandler ch = (ConsoleHandler) consoleHandler;
                            assertSame(System.out, ch.getOut(), "ConsoleHandler should use System.out");
                            assertInstanceOf(PatternLayout.class, ch.getLayout(), "ConsoleHandler layout should be PatternLayout");
                            PatternLayout layout = (PatternLayout) ch.getLayout();
                            assertEquals("%d{yy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n", layout.getText(), "PatternLayout text should match expected");
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
                            assertNotNull(handler, "Handler 'console' should be present");
                            assertInstanceOf(ConsoleHandler.class, handler, "Handler 'console' should be ConsoleHandler");
                            assertEquals("STDOUT", handler.name(), "Handler name should be STDOUT");
                            PatternLayout layout = (PatternLayout) ((ConsoleHandler) handler).getLayout();
                            assertEquals(PatternLayout.parseLog4jPattern("%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n").getText(), layout.getText(), "PatternLayout text should match expected");

                            LogFilter rootFilter = config.getRootFilter();
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled in root filter");
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
                            assertNotNull(handler, "Handler 'file' should be present");
                            assertInstanceOf(FileHandler.class, handler, "Handler 'file' should be FileHandler");
                            FileHandler fh = (FileHandler) handler;
                            assertEquals("File", fh.name(), "Handler name should be File");
                            assertEquals(Path.of("build/test.log").toAbsolutePath(), fh.getPath().toAbsolutePath(), "File path should match expected");
                            assertInstanceOf(PatternLayout.class, fh.getLayout(), "FileHandler layout should be PatternLayout");
                            assertEquals("%d %p %C{1.} [%t] %m%n", ((PatternLayout) fh.getLayout()).getText(), "PatternLayout text should match expected");

                            LogFilter rootFilter = config.getRootFilter();
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.INFO), "LogLevel INFO should be enabled in root filter");
                            assertFalse(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should not be enabled in root filter");
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
                            assertNotNull(handler, "Handler 'rolling' should be present");
                            assertInstanceOf(RotatingFileHandler.class, handler, "Handler 'rolling' should be RotatingFileHandler");
                            RotatingFileHandler rfh = (RotatingFileHandler) handler;
                            assertEquals("RollingFile", rfh.name(), "Handler name should be RollingFile");
                            assertEquals(Path.of("build/rolling.log").toAbsolutePath(), rfh.getPath().toAbsolutePath(), "File path should match expected");
                            assertEquals("build/rolling-%d{MM-dd-yy-HH-mm-ss}-%i.log.gz", rfh.getFilePattern(), "File pattern should match expected");
                            assertEquals(100L * 1024 * 1024, rfh.getMaxFileSize(), "Max file size should match expected");
                            assertEquals(5, rfh.getMaxBackupIndex(), "Max backup index should match expected");
                            assertInstanceOf(PatternLayout.class, rfh.getLayout(), "RotatingFileHandler layout should be PatternLayout");
                            assertEquals("%d %p %C{1.} [%t] %m%n", ((PatternLayout) rfh.getLayout()).getText(), "PatternLayout text should match expected");

                            LogFilter rootFilter = config.getRootFilter();
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.INFO), "LogLevel INFO should be enabled in root filter");
                            assertFalse(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should not be enabled in root filter");
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
                            assertEquals(2, config.getHandlers().size(), "Should have two handlers");
                            LogHandler console = config.getHandler("console");
                            assertNotNull(console, "Handler 'console' should be present");
                            assertEquals("STDOUT", console.name(), "Console handler name should be STDOUT");
                            assertInstanceOf(ConsoleHandler.class, console, "Console handler should be ConsoleHandler");

                            LogHandler file = config.getHandler("file");
                            assertNotNull(file, "Handler 'file' should be present");
                            assertEquals("FILE", file.name(), "File handler name should be FILE");
                            assertInstanceOf(FileHandler.class, file, "File handler should be FileHandler");
                            assertEquals(Path.of("build/test.log").toAbsolutePath(), ((FileHandler) file).getPath().toAbsolutePath(), "File path should match expected");

                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled in root filter");
                        }
                ),
                new TestCase("Logger Levels", """
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
                            assertNotNull(console, "Handler 'console' should be present");
                            assertEquals("STDOUT", console.name(), "Console handler name should be STDOUT");

                            LogFilter appFilter = console.getFilter();
                            assertNotNull(appFilter, "Filter for 'console' should be present");
                            assertTrue(appFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled for 'console'");

                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.INFO), "LogLevel INFO should be enabled in root filter");
                            assertFalse(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should not be enabled in root filter");
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
                            assertNotNull(handler, "Handler 'console' should be present");
                            assertEquals("STDOUT", handler.name(), "Handler name should be STDOUT");
                            assertInstanceOf(ConsoleHandler.class, handler, "Handler 'console' should be ConsoleHandler");
                            ConsoleHandler ch = (ConsoleHandler) handler;
                            assertInstanceOf(PatternLayout.class, ch.getLayout(), "ConsoleHandler layout should be PatternLayout");
                            assertEquals("%msg%n", ((PatternLayout) ch.getLayout()).getText(), "PatternLayout text should match expected");

                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled in root filter");
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
                            assertNotNull(handler, "Handler 'console' should be present");
                            assertEquals("STDOUT", handler.name(), "Handler name should be STDOUT");
                            assertInstanceOf(ConsoleHandler.class, handler, "Handler 'console' should be ConsoleHandler");
                            ConsoleHandler ch = (ConsoleHandler) handler;
                            assertInstanceOf(PatternLayout.class, ch.getLayout(), "ConsoleHandler layout should be PatternLayout");
                            assertEquals("[%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %C{1} - %msg%n", ((PatternLayout) ch.getLayout()).getText(), "PatternLayout text should match expected");

                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled in root filter");
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
                            LogHandler consoleHandler = config.getHandler("console");
                            assertNotNull(consoleHandler, "Handler 'console' should be present");
                            assertEquals("STDOUT", consoleHandler.name(), "Console handler name should be STDOUT");
                            assertInstanceOf(ConsoleHandler.class, consoleHandler, "Console handler should be ConsoleHandler");
                            ConsoleHandler ch = (ConsoleHandler) consoleHandler;
                            assertInstanceOf(PatternLayout.class, ch.getLayout(), "ConsoleHandler layout should be PatternLayout");
                            assertEquals("[%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %C{1} - %msg%n", ((PatternLayout) ch.getLayout()).getText(), "Console PatternLayout text should match expected");

                            LogHandler fileHandler = config.getHandler("file");
                            assertNotNull(fileHandler, "Handler 'file' should be present");
                            assertEquals("LOGFILE", fileHandler.name(), "File handler name should be LOGFILE");
                            // This might be RotatingFileHandler if it has filters or other triggers
                            assertInstanceOf(LogHandler.class, fileHandler, "File handler should be a LogHandler");
                            if (fileHandler instanceof FileHandler fh) {
                                assertEquals(Path.of("build/tmp/baeldung/logs/log4j2.log").toAbsolutePath(), fh.getPath().toAbsolutePath(), "File path should match expected");
                                assertInstanceOf(PatternLayout.class, fh.getLayout(), "FileHandler layout should be PatternLayout");
                                assertEquals("[%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %C{1} - %msg%n", ((PatternLayout) fh.getLayout()).getText(), "File PatternLayout text should match expected");
                            } else if (fileHandler instanceof RotatingFileHandler rfh) {
                                assertEquals(Path.of("build/tmp/baeldung/logs/log4j2.log").toAbsolutePath(), rfh.getPath().toAbsolutePath(), "RotatingFileHandler path should match expected");
                                assertInstanceOf(PatternLayout.class, rfh.getLayout(), "RotatingFileHandler layout should be PatternLayout");
                                assertEquals("[%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %C{1} - %msg%n", ((PatternLayout) rfh.getLayout()).getText(), "RotatingFile PatternLayout text should match expected");
                            }

                            LogFilter fileFilter = fileHandler.getFilter();
                            assertNotNull(fileFilter, "Filter for 'file' should be present");
                            assertTrue(fileFilter.isLevelEnabled(LogLevel.INFO), "LogLevel INFO should be enabled for 'file'");
                            assertFalse(fileFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should not be enabled for 'file'");

                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.INFO), "LogLevel INFO should be enabled in root filter");
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
                            assertNotNull(handler, "Handler 'console' should be present");
                            assertInstanceOf(ConsoleHandler.class, handler, "Handler 'console' should be ConsoleHandler");
                            ConsoleHandler ch = (ConsoleHandler) handler;
                            assertEquals("STDOUT", ch.name(), "Handler name should be STDOUT");
                            assertEquals("JsonLayout", ch.getLayout().getType(), "Layout type should be JsonLayout");

                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled in root filter");
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
                            assertNotNull(handler, "Handler 'console' should be present");
                            assertEquals("STDOUT", handler.name(), "Handler name should be STDOUT");
                            assertInstanceOf(ConsoleHandler.class, handler, "Handler 'console' should be ConsoleHandler");

                            LogFilter filter = handler.getFilter();
                            assertNotNull(filter, "Filter for 'console' should be present");
                            assertTrue(filter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled for 'console'");

                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled in root filter");
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
                            assertNotNull(handler, "Handler 'console' should be present");
                            assertEquals("STDOUT", handler.name(), "Handler name should be STDOUT");
                            assertInstanceOf(ConsoleHandler.class, handler, "Handler 'console' should be ConsoleHandler");
                            ConsoleHandler ch = (ConsoleHandler) handler;
                            assertInstanceOf(SimpleLayout.class, ch.getLayout(), "ConsoleHandler layout should be SimpleLayout");

                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled in root filter");
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
                            // Currently unsupported, should not crash and returns no handler
                            assertTrue(config.getHandlers().isEmpty(), "Unsupported appender should result in no handlers");
                            
                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should be enabled in root filter");
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
                            assertNotNull(handler, "Handler 'rolling' should be present");
                            assertInstanceOf(RotatingFileHandler.class, handler, "Handler 'rolling' should be RotatingFileHandler");
                            RotatingFileHandler rfh = (RotatingFileHandler) handler;
                            assertEquals("RollingFile", rfh.name(), "Handler name should be RollingFile");
                            assertEquals(Path.of("build/rolling.log").toAbsolutePath(), rfh.getPath().toAbsolutePath(), "File path should match expected");
                            assertEquals("build/rolling-%d{MM-dd-yy-HH-mm-ss}-%i.log.gz", rfh.getFilePattern(), "File pattern should match expected");
                            assertEquals(100L * 1024 * 1024, rfh.getMaxFileSize(), "Max file size should match expected");
                            assertEquals(5, rfh.getMaxBackupIndex(), "Max backup index should match expected");
                            assertInstanceOf(PatternLayout.class, rfh.getLayout(), "RotatingFileHandler layout should be PatternLayout");

                            LogFilter rootFilter = config.getRootFilter();
                            assertNotNull(rootFilter, "Root filter should be present");
                            assertInstanceOf(LogLevelFilter.class, rootFilter, "Root filter should be LogLevelFilter");
                            assertTrue(rootFilter.isLevelEnabled(LogLevel.INFO), "LogLevel INFO should be enabled in root filter");
                            assertFalse(rootFilter.isLevelEnabled(LogLevel.DEBUG), "LogLevel DEBUG should not be enabled in root filter");
                        }
                ),
                new TestCase("package log level", """
                        status = WARN
                        
                        appender.console.type = Console
                        appender.console.name = Console
                        appender.console.target = SYSTEM_OUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = %d{yy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n
                        
                        loggers = comsun
                        logger.comsun.name = com.sun
                        logger.comsun.level = WARN
                        
                        rootLogger.level = debug
                        rootLogger.appenderRef.console.ref = Console
                        """,
                        config -> {
                        }
                )
        );
    }
}
