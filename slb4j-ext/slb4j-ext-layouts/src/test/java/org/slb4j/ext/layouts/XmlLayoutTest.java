package org.slb4j.ext.layouts;

import org.junit.jupiter.api.Test;
import org.slb4j.ConsoleCode;
import org.slb4j.LogLevel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class XmlLayoutTest {

    @Test
    void testLayoutMetadataAndSingleton() {
        XmlLayout first = XmlLayout.instance();
        assertSame(first, XmlLayout.instance());
        assertEquals("XmlLayout", first.getType());
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<logEvents>\n", first.getHeader());
        assertEquals("</logEvents>\n", first.getFooter());
    }

    @Test
    void testEscapedOutputIsWellFormedXml() throws Exception {
        XmlLayout layout = new XmlLayout(java.time.ZoneOffset.UTC);
        StringBuilder event = new StringBuilder();

        layout.formatLogEntry(event, 0, "logger <&", LogLevel.INFO, null, null, null,
                "<message> & \"quotes\" 'apostrophe'", null, ConsoleCode.empty());

        Document document = parseDocument(layout.getHeader() + event + layout.getFooter());
        assertEquals("logger <&", document.getElementsByTagName("logger").item(0).getTextContent());
        assertEquals("<message> & \"quotes\" 'apostrophe'",
                document.getElementsByTagName("message").item(0).getTextContent());
    }

    @Test
    void testForbiddenControlsAreReplaced() throws Exception {
        XmlLayout layout = new XmlLayout(java.time.ZoneOffset.UTC);
        StringBuilder event = new StringBuilder();

        layout.formatLogEntry(event, 0, "logger", LogLevel.INFO, null, null, null,
                "a\u0001b\u000B c\u001F\uFFFE", null, ConsoleCode.empty());

        Document document = parseDocument(layout.getHeader() + event + layout.getFooter());
        assertEquals("a�b� c��", document.getElementsByTagName("message").item(0).getTextContent());
    }

    @Test
    void testNullAndAllowedXmlCharacters() throws Exception {
        XmlLayout layout = new XmlLayout(java.time.ZoneOffset.UTC);
        StringBuilder events = new StringBuilder();
        String allowedCharacters = "\t\n\r \uD7FF\uE000\uFFFD";

        layout.formatLogEntry(events, 0, "logger", LogLevel.INFO, null, null, null,
                null, null, ConsoleCode.empty());
        layout.formatLogEntry(events, 1, "logger", LogLevel.INFO, null, null, null,
                allowedCharacters, null, ConsoleCode.empty());

        Document document = parseDocument(layout.getHeader() + events + layout.getFooter());
        NodeList messages = document.getElementsByTagName("message");
        assertEquals(2, messages.getLength());
        assertEquals("null", messages.item(0).getTextContent());
        // XML 1.0 normalizes carriage returns to line feeds while parsing.
        assertEquals(allowedCharacters.replace('\r', '\n'), messages.item(1).getTextContent());
    }

    @Test
    void testMalformedUnicodeIsReplacedAndValidPairsArePreserved() throws Exception {
        XmlLayout layout = new XmlLayout(java.time.ZoneOffset.UTC);
        StringBuilder event = new StringBuilder();

        layout.formatLogEntry(event, 0, "logger", LogLevel.INFO, null, null, null,
                "😀|\uD800|\uDC00|\uD800", null, ConsoleCode.empty());

        Document document = parseDocument(layout.getHeader() + event + layout.getFooter());
        assertEquals("😀|�|�|�", document.getElementsByTagName("message").item(0).getTextContent());
    }

    @Test
    void testMultipleEventsFormOneWellFormedDocument() throws Exception {
        XmlLayout layout = new XmlLayout(java.time.ZoneOffset.UTC);
        StringBuilder events = new StringBuilder();
        layout.formatLogEntry(events, 0, "first", LogLevel.INFO, null, null, null, "one", null, ConsoleCode.empty());
        layout.formatLogEntry(events, 1, "second", LogLevel.ERROR, null, null, null, "two", null, ConsoleCode.empty());

        Document document = parseDocument(layout.getHeader() + events + layout.getFooter());
        NodeList logEvents = document.getElementsByTagName("logEvent");
        assertEquals(2, logEvents.getLength());
        assertEquals("one", ((Element) logEvents.item(0)).getElementsByTagName("message").item(0).getTextContent());
        assertEquals("two", ((Element) logEvents.item(1)).getElementsByTagName("message").item(0).getTextContent());
    }

    private static Document parseDocument(String output) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(output)));
    }
}
