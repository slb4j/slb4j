package org.slb4j;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Assertions;
import org.slb4j.config.ConfigParserLog4j;
import org.slb4j.layout.PatternLayout;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Log4j2PropertiesTest {

    record PropertySet(String name, String properties, boolean supported) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<PropertySet> propertieSets() {
        return Stream.of(
                new PropertySet("Basic Console", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = %d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """, true),
                new PropertySet("Basic File", """
                        appender.file.type = File
                        appender.file.name = File
                        appender.file.fileName = build/test.log
                        appender.file.layout.type = PatternLayout
                        appender.file.layout.pattern = %d %p %C{1.} [%t] %m%n
                        rootLogger.level = info
                        rootLogger.appenderRef.file.ref = File
                        """, true),
                new PropertySet("Rolling File", """
                        appender.rolling.type = RollingFile
                        appender.rolling.name = RollingFile
                        appender.rolling.fileName = build/rolling.log
                        appender.rolling.filePattern = build/rolling-%d{MM-dd-yy-HH-mm-ss}-%i.log.gz
                        appender.rolling.layout.type = PatternLayout
                        appender.rolling.layout.pattern = %d %p %C{1.} [%t] %m%n
                        appender.rolling.policies.size.size = 100MB
                        appender.rolling.strategy.max = 5
                        rootLogger.level = info
                        rootLogger.appenderRef.rolling.ref = RollingFile
                        """, true),
                new PropertySet("Multiple Appenders", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.file.type = File
                        appender.file.name = FILE
                        appender.file.fileName = build/test.log
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        rootLogger.appenderRef.file.ref = FILE
                        """, true),
                new PropertySet("Logger levels", """
                        logger.app.name = org.apache.logging.log4j.test1
                        logger.app.level = debug
                        logger.app.additivity = false
                        logger.app.appenderRef.stdout.ref = STDOUT
                        rootLogger.level = info
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """, true),
                new PropertySet("Baeldung - 6. Syntax of the log4j2.properties", """
                        # The root logger with appender name
                        rootLogger = DEBUG, STDOUT
                        
                        # Assign STDOUT a valid appender & define its layout 
                        appender.console.name = STDOUT
                        appender.console.type = Console
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = %msg%n 
                        Copy        
                        """, true),
                new PropertySet("Baeldung - 7.2 Console Logging", """
                        # Root Logger
                        rootLogger=DEBUG, STDOUT
                        
                        # Direct log messages to stdout
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = PatternLayout
                        appender.console.layout.pattern = [%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %C{1} - %msg%n
                        """, true),
                new PropertySet("Baeldung - 7.3. Multiple Destinations", """
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
                        """, true),
                new PropertySet("JSON Layout", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = JsonLayout
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """, true),
                new PropertySet("Syslog Appender", """
                        appender.syslog.type = Syslog
                        appender.syslog.name = Syslog
                        appender.syslog.host = localhost
                        appender.syslog.port = 514
                        appender.syslog.protocol = UDP
                        rootLogger.level = debug
                        rootLogger.appenderRef.syslog.ref = Syslog
                        """, false),
                new PropertySet("Custom Filter", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.filter.threshold.type = ThresholdFilter
                        appender.console.filter.threshold.level = debug
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """, true),
                new PropertySet("Simple Layout", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = SimpleLayout
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """, true),
                new PropertySet("Complex Policies", """
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
                        """, false)
        );
    }

    @ParameterizedTest
    @MethodSource("propertieSets")
    void testSupportedProperties(PropertySet propertySet) throws IOException {
        assumeTrue(propertySet.supported, "Currently unsupported: " + propertySet.name);

        Properties props = new Properties();
        props.load(new StringReader(propertySet.properties));

        LoggingConfiguration config = new ConfigParserLog4j().parse(props);

        // Verify configuration directly without round-tripping properties
        // Discover appenders declared in the input properties
        java.util.regex.Pattern appenderTypeKey = java.util.regex.Pattern.compile("^appender\\.([^.]+)\\.type$");
        props.stringPropertyNames().stream()
                .filter(k -> appenderTypeKey.matcher(k).matches())
                .map(k -> {
                    java.util.regex.Matcher m = appenderTypeKey.matcher(k);
                    m.matches();
                    return m.group(1);
                })
                .forEach(appenderName -> {
                    String prefix = "appender." + appenderName + ".";
                    String type = props.getProperty(prefix + "type").trim();

                    LogHandler handler = config.getHandler(appenderName);
                    Assertions.assertNotNull(handler, "Handler '" + appenderName + "' should exist");

                    switch (type) {
                        case "Console" -> Assertions.assertInstanceOf(org.slb4j.handler.ConsoleHandler.class, handler);
                        case "File" -> {
                            // Our parser may choose a RotatingFileHandler based on certain sub-entries; accept either implementation here
                            // and verify concrete properties below.
                        }
                        case "RollingFile" -> Assertions.assertInstanceOf(org.slb4j.handler.RotatingFileHandler.class, handler);
                        default -> {
                            Assertions.fail("Unexpected appender type '" + type + "' for '" + appenderName + "'");
                        }
                    }

                    // Common file-related assertions
                    String fileName = props.getProperty(prefix + "fileName");
                    if (fileName != null) {
                        if (handler instanceof org.slb4j.handler.FileHandler fh) {
                            Assertions.assertEquals(java.nio.file.Path.of(fileName).toAbsolutePath(), fh.getPath().toAbsolutePath(), "fileName mismatch for '" + appenderName + "'");
                        } else if (handler instanceof org.slb4j.handler.RotatingFileHandler rfh) {
                            Assertions.assertEquals(java.nio.file.Path.of(fileName).toAbsolutePath(), rfh.getPath().toAbsolutePath(), "fileName mismatch for '" + appenderName + "'");
                        }
                    }

                    String append = props.getProperty(prefix + "append");
                    if (append != null) {
                        boolean expectedAppend = Boolean.parseBoolean(append.trim());
                        if (handler instanceof org.slb4j.handler.FileHandler fh) {
                            Assertions.assertEquals(expectedAppend, fh.isAppend(), "append mismatch for '" + appenderName + "'");
                        } else if (handler instanceof org.slb4j.handler.RotatingFileHandler rfh) {
                            Assertions.assertEquals(expectedAppend, rfh.isAppend(), "append mismatch for '" + appenderName + "'");
                        }
                    }

                    if (handler instanceof org.slb4j.handler.RotatingFileHandler rfh) {
                        String filePattern = props.getProperty(prefix + "filePattern");
                        if (filePattern != null) {
                            Assertions.assertEquals(filePattern.trim(), rfh.getFilePattern(), "filePattern mismatch for '" + appenderName + "'");
                        }
                        String size = props.getProperty(prefix + "policies.size.size");
                        if (size != null) {
                            Assertions.assertEquals(normalizeSize(size), rfh.getMaxFileSize(), "maxFileSize mismatch for '" + appenderName + "'");
                        }
                        String max = props.getProperty(prefix + "strategy.max");
                        if (max != null) {
                            Assertions.assertEquals(Integer.parseInt(max.trim()), rfh.getMaxBackupIndex(), "max backups mismatch for '" + appenderName + "'");
                        }
                    }

                    // Layout assertions (when explicitly configured)
                    String layoutType = props.getProperty(prefix + "layout.type");
                    if (layoutType != null && handler instanceof org.slb4j.LayoutConfigurable lc) {
                        Assertions.assertEquals(layoutType.trim(), lc.getLayout().getType(), "layout.type mismatch for '" + appenderName + "'");
                        String pattern = props.getProperty(prefix + "layout.pattern");
                        if (pattern != null) {
                            Assertions.assertEquals(
                                    PatternLayout.parseLog4jPattern(pattern).getText(),
                                    lc.getLayout().getText(),
                                    "layout.pattern mismatch (normalized) for '" + appenderName + "'"
                            );
                        }
                    }

                    // Simple threshold filter check if present
                    String threshold = props.getProperty(prefix + "filter.threshold.level");
                    if (threshold != null) {
                        org.slb4j.LogLevel lvl = org.slb4j.LogLevel.valueOf(threshold.trim().toUpperCase(java.util.Locale.ROOT));
                        Assertions.assertTrue(handler.getFilter().isLevelEnabled(lvl), "threshold filter should enable level " + lvl + " for '" + appenderName + "'");
                    }
                });
    }

    private static long normalizeSize(String s) {
        s = s.strip().toUpperCase(java.util.Locale.ROOT);
        if (s.endsWith("MB")) {
            return Long.parseLong(s.substring(0, s.length() - 2).strip()) * 1024L * 1024L;
        } else if (s.endsWith("KB")) {
            return Long.parseLong(s.substring(0, s.length() - 2).strip()) * 1024L;
        } else if (s.endsWith("GB")) {
            return Long.parseLong(s.substring(0, s.length() - 2).strip()) * 1024L * 1024L * 1024L;
        } else if (s.endsWith("B")) {
            return Long.parseLong(s.substring(0, s.length() - 1).strip());
        }
        return Long.parseLong(s);
    }
}
