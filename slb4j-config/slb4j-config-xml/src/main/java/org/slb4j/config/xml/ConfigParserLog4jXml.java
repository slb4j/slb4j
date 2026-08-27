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

package org.slb4j.config.xml;

import org.slb4j.LoggingConfiguration;
import org.slb4j.config.ConfigParser;
import org.slb4j.config.ConfigParserLog4jProperties;
import org.slb4j.config.support.Log4j2TreeFlattener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * XML configuration parser for SLB4J.
 */
public class ConfigParserLog4jXml implements ConfigParser {

    /**
     * Constructs a new {@code ConfigParserLog4jXml}.
     */
    public ConfigParserLog4jXml() {
        // nothing to do
    }

    @Override
    public LoggingConfiguration parse(InputStream in) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(in);
            Element rootElement = doc.getDocumentElement();

            Log4j2TreeFlattener flattener = new Log4j2TreeFlattener();
            Properties properties = flattener.flatten(new DomNode(rootElement));

            return new ConfigParserLog4jProperties().parse(properties);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Could not enable secured XML processing", e);
        } catch (SAXException e) {
            throw new IOException("The XML configuration file is not valid: " + e.getMessage(), e);
        }
    }

    private static class DomNode implements Log4j2TreeFlattener.Node {
        private final Element element;

        DomNode(Element element) {
            this.element = element;
        }

        @Override
        public String getName() {
            return element.getTagName();
        }

        @Override
        public Map<String, String> getAttributes() {
            Map<String, String> attributes = new HashMap<>();
            NamedNodeMap nnm = element.getAttributes();
            for (int i = 0; i < nnm.getLength(); i++) {
                org.w3c.dom.Node node = nnm.item(i);
                attributes.put(node.getNodeName(), node.getNodeValue());
            }
            return attributes;
        }

        @Override
        public List<Log4j2TreeFlattener.Node> getChildren() {
            List<Log4j2TreeFlattener.Node> children = new ArrayList<>();
            NodeList nl = element.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                org.w3c.dom.Node node = nl.item(i);
                if (node instanceof Element el) {
                    children.add(new DomNode(el));
                }
            }
            return children;
        }

        @Override
        public String getTextValue() {
            return element.getTextContent();
        }
    }
}
