/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slb4j;

import org.junit.jupiter.api.Disabled;
import org.slb4j.config.ConfigParserJul;
import org.slb4j.config.ConfigParserLog4j;
import org.slb4j.layout.CsvLayout;
import org.slb4j.layout.XmlLayout;
import org.slb4j.support.Util;
import org.slb4j.handler.RotatingFileHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slb4j.layout.StandardLayout;

import java.nio.file.Files;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class LoggingConfigurationTest {

    @Test
    void testFileHandlerConfiguration(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("test.log");
        String propertyText = """
                appender.file.type=File
                appender.file.fileName=%s
                appender.file.append=false
                appender.file.filePattern=test-%%i.log
                appender.file.policies.size.size=1024
                appender.file.policies.time.interval=1
                appender.file.strategy.max=5
                appender.file.layout.type=PatternLayout
                appender.file.layout.pattern=%%m%%n
                """.formatted(Util.pathToNormalizedString(logFile));
        Properties props = new Properties();
        props.load(new StringReader(propertyText));

        LoggingConfiguration config = new ConfigParserLog4j().parse(props);

        LogHandler handler = config.getHandlers().values().stream()
                .filter(h -> "file".equals(h.name()))
                .findFirst()
                .orElseThrow();

        // Verify configuration directly
        assertInstanceOf(RotatingFileHandler.class, handler);
        RotatingFileHandler fileHandler = (RotatingFileHandler) handler;

        assertEquals(logFile.toAbsolutePath(), fileHandler.getPath().toAbsolutePath());
        assertFalse(fileHandler.isAppend());
        assertEquals("test-%i.log", fileHandler.getFilePattern());
        assertEquals(1024L, fileHandler.getMaxFileSize());
        assertNotNull(fileHandler.getRotationTimeUnit());
        assertEquals(5, fileHandler.getMaxBackupIndex());
        assertEquals("%m%n", fileHandler.getLayout().getText());
    }

    @Test
    void testSimpleFileHandlerConfiguration(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("simple.log");
        String propertyText = """
                appender.file.type=File
                appender.file.fileName=%s
                appender.file.append=false
                """.formatted(Util.pathToNormalizedString(logFile));
        Properties props = new Properties();
        props.load(new StringReader(propertyText));

        LoggingConfiguration config = new ConfigParserLog4j().parse(props);

        // RotatingFileHandler is used for "File" type in parseLog4j if it's successfully configured
        LogHandler handler = config.getHandler("file");
        assertNotNull(handler);
        assertEquals(logFile.toAbsolutePath(), ((org.slb4j.handler.FileHandler)handler).getPath().toAbsolutePath());
        assertFalse(((org.slb4j.handler.FileHandler)handler).isAppend());
    }

    @Test
    void testFileHandlerExplicit(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("explicit.log");
        LoggingConfiguration config = LoggingConfiguration.defaultConfiguration();
        config.addHandler("file", new org.slb4j.handler.FileHandler("file", logFile, true));
        LogHandler handler = config.getHandler("file");
        assertNotNull(handler);
        assertInstanceOf(org.slb4j.handler.FileHandler.class, handler);
        org.slb4j.handler.FileHandler fileHandler = (org.slb4j.handler.FileHandler) handler;

        assertEquals(logFile.toAbsolutePath(), fileHandler.getPath().toAbsolutePath());
        assertTrue(fileHandler.isAppend());
    }

    @Test
    @Disabled
    void testParseJul() throws IOException {
        String propertyText = """
                handlers=java.util.logging.ConsoleHandler, java.util.logging.FileHandler
                .level=INFO
                org.slb4j.level=FINE
                java.util.logging.FileHandler.pattern=test.log
                java.util.logging.FileHandler.limit=1024
                java.util.logging.FileHandler.count=3
                java.util.logging.FileHandler.append=true
                """;
        Properties props = new Properties();
        props.load(new StringReader(propertyText));

        LoggingConfiguration config = new ConfigParserJul().parse(props);

        // Check handlers
        assertTrue(config.getHandlers().values().stream().anyMatch(h -> "console".equals(h.name())));
        LogHandler fileHandler = config.getHandlers().values().stream()
                .filter(h -> "file".equals(h.name()))
                .findFirst()
                .orElseThrow();
        assertInstanceOf(RotatingFileHandler.class, fileHandler);
        RotatingFileHandler rotatingFileHandler = (RotatingFileHandler) fileHandler;
        assertEquals("test.log", Util.pathToNormalizedString(rotatingFileHandler.getPath()));
        assertEquals(1024L, rotatingFileHandler.getMaxFileSize());
        assertEquals(2, rotatingFileHandler.getMaxBackupIndex()); // count 3 means 2 backups
        assertTrue(rotatingFileHandler.isAppend());

        // Check levels
        LogFilter filter = config.getFilters().getFirst();
        assertTrue(filter.isEnabled("any.logger", LogLevel.INFO, null), "INFO should be enabled for any.logger");
        assertFalse(filter.isEnabled("any.logger", LogLevel.DEBUG, null), "DEBUG should NOT be enabled for any.logger");
        assertTrue(filter.isEnabled("org.slb4j.Test", LogLevel.DEBUG, null), "DEBUG should be enabled for org.slb4j.Test (mapped from FINE)");
    }

    @Test
    @Disabled
    void testParseJulRootLevel() throws IOException {
        String propertyText = """
                .level=SEVERE
                """;
        Properties props = new Properties();
        props.load(new StringReader(propertyText));

        LoggingConfiguration config = new ConfigParserJul().parse(props);

        LogFilter filter = config.getFilters().getFirst();
        // ERROR should be enabled
        assertTrue(filter.isEnabled("any.logger", LogLevel.ERROR, null), "ERROR should be enabled for any.logger");
        // WARN should NOT be enabled if SEVERE is correctly parsed
        assertFalse(filter.isEnabled("any.logger", LogLevel.WARN, null), "WARN should NOT be enabled for any.logger");
    }

    @Test
    void testCsvLayoutConfiguration() throws IOException {
        String propertyText = """
                appender.console.type=Console
                appender.console.layout.type=CsvLayout
                """;
        Properties props = new Properties();
        props.load(new StringReader(propertyText));

        LoggingConfiguration config = new ConfigParserLog4j().parse(props);

        LogHandler handler = config.getHandler("console");
        assertNotNull(handler);
        assertInstanceOf(org.slb4j.handler.ConsoleHandler.class, handler);
        org.slb4j.handler.ConsoleHandler consoleHandler = (org.slb4j.handler.ConsoleHandler) handler;
        assertEquals(StandardLayout.CSV.type(), consoleHandler.getLayout().getType());
    }

    @Test
    void testCsvOutputFormat() throws IOException {
        LogLayout csvPattern = CsvLayout.instance();
        StringBuilder sb = new StringBuilder();
        long timestamp = 1738259700000L; // 2025-01-30 17:55:00 UTC (roughly)
        // Note: TimeStampFormatter uses system default timezone by default in CsvEntry
        // Actually CsvEntry uses LogPattern.ZONE_ID which is ZoneId.systemDefault()
        
        Location loc = null;
        csvPattern.formatLogEntry(sb, timestamp, "test.Logger", LogLevel.INFO, null, null, loc, "Hello \"World\"", null, ConsoleCode.empty());
        
        String output = sb.toString();
        // Format: "timestamp","LEVEL","logger","message"
        // We can't easily predict the exact timestamp string without knowing the system timezone, 
        // but we can check the rest of the structure.
        assertTrue(output.startsWith("\""), "Output should start with a quote");
        assertTrue(output.endsWith("\"\n"), "Output should end with a quote and newline");
        assertTrue(output.contains("\",\"INFO\",\"test.Logger\",\"Hello \"\"World\"\"\"\n"), "Output should contain correctly formatted fields. Got: " + output);
    }

    @Test
    void testCsvOutputFormatWithNullMessage() throws IOException {
        LogLayout csvPattern = CsvLayout.instance();
        StringBuilder sb = new StringBuilder();
        long timestamp = 1738259700000L;
        Location loc = null;
        csvPattern.formatLogEntry(sb, timestamp, "test.Logger", LogLevel.ERROR, null, null, loc, null, null, ConsoleCode.empty());

        String output = sb.toString();
        assertTrue(output.contains("\",\"ERROR\",\"test.Logger\",\"null\"\n"), "Output should contain 'null' for null message. Got: " + output);
    }

    @Test
    void testXmlLayoutConfiguration() throws IOException {
        String propertyText = """
                appender.console.type=Console
                appender.console.layout.type=XmlLayout
                """;
        Properties props = new Properties();
        props.load(new StringReader(propertyText));

        LoggingConfiguration config = new ConfigParserLog4j().parse(props);

        LogHandler handler = config.getHandler("console");
        assertNotNull(handler);
        assertInstanceOf(org.slb4j.handler.ConsoleHandler.class, handler);
        org.slb4j.handler.ConsoleHandler consoleHandler = (org.slb4j.handler.ConsoleHandler) handler;
        assertEquals(StandardLayout.XML.type(), consoleHandler.getLayout().getType());
    }

    @Test
    void testXmlOutputFormat() throws IOException {
        LogLayout xmlPattern = XmlLayout.instance();
        StringBuilder sb = new StringBuilder();
        long timestamp = 1738259700000L;

        Location loc = null;
        xmlPattern.formatLogEntry(sb, timestamp, "test.Logger", LogLevel.INFO, null, null, loc, "Hello <World> & \"Friends\"", null, ConsoleCode.empty());

        String output = sb.toString();
        assertTrue(output.contains("<logEvent>"), "Output should contain <logEvent>");
        assertTrue(output.contains("<level>INFO</level>"), "Output should contain <level>INFO</level>");
        assertTrue(output.contains("<logger>test.Logger</logger>"), "Output should contain <logger>test.Logger</logger>");
        assertTrue(output.contains("<message>Hello &lt;World&gt; &amp; &quot;Friends&quot;</message>"), "Output should contain escaped message. Got: " + output);
        assertTrue(output.contains("</logEvent>"), "Output should contain </logEvent>");
    }

    @Test
    void testXmlHeaderFooter() {
        LogLayout xmlPattern = XmlLayout.instance();
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<logEvents>\n", xmlPattern.getHeader());
        assertEquals("</logEvents>\n", xmlPattern.getFooter());
    }

    @Test
    void testJsonLayoutConfiguration() throws IOException {
        String propertyText = """
                appender.console.type=Console
                appender.console.layout.type=JsonLayout
                appender.console.layout.zoneId=UTC
                """;
        Properties props = new Properties();
        props.load(new StringReader(propertyText));

        LoggingConfiguration config = new ConfigParserLog4j().parse(props);

        LogHandler handler = config.getHandler("console");
        assertNotNull(handler);
        assertInstanceOf(org.slb4j.handler.ConsoleHandler.class, handler);
        org.slb4j.handler.ConsoleHandler consoleHandler = (org.slb4j.handler.ConsoleHandler) handler;
        assertEquals(StandardLayout.JSON.type(), consoleHandler.getLayout().getType());
    }

    @Test
    void testYamlLayoutConfiguration() throws IOException {
        String propertyText = """
                appender.console.type=Console
                appender.console.layout.type=YamlLayout
                """;
        Properties props = new Properties();
        props.load(new StringReader(propertyText));

        LoggingConfiguration config = new ConfigParserLog4j().parse(props);

        LogHandler handler = config.getHandler("console");
        assertNotNull(handler);
        assertInstanceOf(org.slb4j.handler.ConsoleHandler.class, handler);
        org.slb4j.handler.ConsoleHandler consoleHandler = (org.slb4j.handler.ConsoleHandler) handler;
        assertEquals(StandardLayout.YAML.type(), consoleHandler.getLayout().getType());
    }

    @Test
    void testFileHandlerHeaderFooter(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("test-xml.log");
        String propertyText = """
                appender.file.type=File
                appender.file.fileName=%s
                appender.file.layout.type=XmlLayout
                """.formatted(Util.pathToNormalizedString(logFile));
        Properties props = new Properties();
        props.load(new StringReader(propertyText));

        LoggingConfiguration config = new ConfigParserLog4j().parse(props);
        LogHandler handler = config.getHandler("file");
        assertNotNull(handler);

        // Closing the handler should write the footer
        handler.shutdown();

        String content = Files.readString(logFile);
        assertTrue(content.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<logEvents>\n"), "File should start with header");
        assertTrue(content.endsWith("</logEvents>\n"), "File should end with footer");
    }
}
