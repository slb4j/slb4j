package org.slb4j;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Assertions;

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
                        appender.file.fileName = target/test.log
                        appender.file.layout.type = PatternLayout
                        appender.file.layout.pattern = %d %p %C{1.} [%t] %m%n
                        rootLogger.level = info
                        rootLogger.appenderRef.file.ref = File
                        """, true),
                new PropertySet("Rolling File", """
                        appender.rolling.type = RollingFile
                        appender.rolling.name = RollingFile
                        appender.rolling.fileName = target/rolling.log
                        appender.rolling.filePattern = target/rolling-%d{MM-dd-yy-HH-mm-ss}-%i.log.gz
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
                        appender.file.fileName = target/test.log
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
                        appender.file.fileName = baeldung/logs/log4j2.log
                        appender.file.layout.type = PatternLayout
                        appender.file.layout.pattern = [%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %C{1} - %msg%n
                        appender.file.filter.threshold.type = ThresholdFilter
                        appender.file.filter.threshold.level = info
                        """, false),
                new PropertySet("JSON Layout", """
                        appender.console.type = Console
                        appender.console.name = STDOUT
                        appender.console.layout.type = JsonLayout
                        rootLogger.level = debug
                        rootLogger.appenderRef.stdout.ref = STDOUT
                        """, false),
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
                        """, false),
                new PropertySet("Complex Policies", """
                        appender.rolling.type = RollingFile
                        appender.rolling.name = RollingFile
                        appender.rolling.fileName = target/rolling.log
                        appender.rolling.filePattern = target/rolling-%d{MM-dd-yy-HH-mm-ss}-%i.log.gz
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
        Properties props = new Properties();
        assumeTrue(propertySet.supported, "Currently unsupported: " + propertySet.name);
        props.load(new StringReader(propertySet.properties));
        Assertions.assertDoesNotThrow(() -> LoggingConfiguration.parseLog4j(props));
    }
}
