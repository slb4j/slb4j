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
package org.slb4j.ext.layout;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogHandler;
import org.slb4j.LogLayout;
import org.slb4j.LogLevel;
import org.slb4j.LoggingConfiguration;
import org.slb4j.config.ConfigParserLog4j;
import org.slb4j.layout.Layouts;
import org.slb4j.support.Util;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdditionalLayoutsLoggingConfigurationTest {

    @BeforeAll
    static void setup() {
        new LayoutsPlugin().init();
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
        assertEquals("CsvLayout", consoleHandler.getLayout().getType());
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
        assertEquals("XmlLayout", consoleHandler.getLayout().getType());
    }

    @Test
    void testXmlOutputFormat() throws IOException {
        LogLayout xmlLayout = Layouts.builder("XmlLayout").apply("XML").build();
        StringBuilder sb = new StringBuilder();
        long timestamp = 1738259700000L;

        Location loc = null;
        xmlLayout.formatLogEntry(sb, timestamp, "test.Logger", LogLevel.INFO, null, null, loc, "Hello <World> & \"Friends\"", null, ConsoleCode.empty());

        String output = sb.toString();
        assertTrue(output.contains("<logEvent>"), "Output should contain <logEvent>");
        assertTrue(output.contains("<level>INFO</level>"), "Output should contain <level>INFO</level>");
        assertTrue(output.contains("<logger>test.Logger</logger>"), "Output should contain <logger>test.Logger</logger>");
        assertTrue(output.contains("<message>Hello &lt;World&gt; &amp; &quot;Friends&quot;</message>"), "Output should contain escaped message. Got: " + output);
        assertTrue(output.contains("</logEvent>"), "Output should contain </logEvent>");
    }

    @Test
    void testXmlHeaderFooter() {
        LogLayout xmlLayout = Layouts.builder("XmlLayout").apply("XML").build();
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<logEvents>\n", xmlLayout.getHeader());
        assertEquals("</logEvents>\n", xmlLayout.getFooter());
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
        assertEquals("YamlLayout", consoleHandler.getLayout().getType());
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
