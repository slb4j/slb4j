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

package org.slb4j.config.support;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Utility to flatten a tree-like configuration (from XML, JSON, YAML) into Log4j2-style properties.
 */
public class Log4j2TreeFlattener {

    /**
     * Constructs a new {@code Log4j2TreeFlattener}.
     */
    public Log4j2TreeFlattener() {
    }

    /**
     * Interface representing a node in a configuration tree.
     */
    public interface Node {
        /**
         * Get the name.
         * @return the tag name or key of the node.
         */
        String getName();

        /**
         * Get the attributes.
         * @return the attributes of the node.
         */
        Map<String, String> getAttributes();

        /**
         * Get the children.
         * @return the children of the node.
         */
        List<? extends Node> getChildren();

        /**
         * Get the text value.
         * @return the text value of the node, if any.
         */
        String getTextValue();
    }

    private final Properties properties = new Properties();

    /**
     * Flattens the tree starting from the root node.
     * @param root the root node (usually "Configuration").
     * @return the flattened properties.
     */
    public Properties flatten(Node root) {
        // Log4j2 root can have attributes like "status"
        String status = root.getAttributes().get("status");
        if (status != null) {
            properties.setProperty("status", status);
        }

        for (Node child : root.getChildren()) {
            String name = child.getName();
            if ("Appenders".equalsIgnoreCase(name)) {
                flattenAppenders(child);
            } else if ("Loggers".equalsIgnoreCase(name)) {
                flattenLoggers(child);
            }
        }
        return properties;
    }

    private void flattenAppenders(Node appendersNode) {
        for (Node appender : appendersNode.getChildren()) {
            String type = appender.getName();
            String name = appender.getAttributes().get("name");
            if (name == null) continue;

            String id = name.replace('.', '_');
            String prefix = "appender." + id;
            properties.setProperty(prefix + ".type", type);
            properties.setProperty(prefix + ".name", name);

            for (Map.Entry<String, String> attr : appender.getAttributes().entrySet()) {
                if (!"name".equals(attr.getKey())) {
                    properties.setProperty(prefix + "." + attr.getKey(), attr.getValue());
                }
            }

            for (Node child : appender.getChildren()) {
                flattenAppenderComponent(prefix, child);
            }
        }
    }

    private void flattenAppenderComponent(String prefix, Node node) {
        String type = node.getName();
        String componentPrefix;
        if (type.endsWith("Layout")) {
             componentPrefix = prefix + ".layout";
             properties.setProperty(componentPrefix + ".type", type);
        } else if (type.endsWith("Filter")) {
             componentPrefix = prefix + ".filter";
             properties.setProperty(componentPrefix + ".type", type);
        } else {
             componentPrefix = prefix + "." + type;
        }

        for (Map.Entry<String, String> attr : node.getAttributes().entrySet()) {
            properties.setProperty(componentPrefix + "." + attr.getKey(), attr.getValue());
        }

        String text = node.getTextValue();
        if (text != null && !text.isBlank()) {
            properties.setProperty(componentPrefix, text);
        }

        for (Node child : node.getChildren()) {
            flattenAppenderComponent(componentPrefix, child);
        }
    }

    private void flattenLoggers(Node loggersNode) {
        for (Node logger : loggersNode.getChildren()) {
            String nodeName = logger.getName();
            if ("Root".equalsIgnoreCase(nodeName)) {
                flattenLogger("rootLogger", logger);
            } else if ("Logger".equalsIgnoreCase(nodeName)) {
                String name = logger.getAttributes().get("name");
                if (name != null) {
                    String id = name.replace('.', '_');
                    String prefix = "logger." + id;
                    properties.setProperty(prefix + ".name", name);
                    flattenLogger(prefix, logger);
                }
            }
        }
    }

    private void flattenLogger(String prefix, Node node) {
        for (Map.Entry<String, String> attr : node.getAttributes().entrySet()) {
            if (!"name".equals(attr.getKey())) {
                properties.setProperty(prefix + "." + attr.getKey(), attr.getValue());
            }
        }

        for (Node child : node.getChildren()) {
            if ("AppenderRef".equalsIgnoreCase(child.getName())) {
                String ref = child.getAttributes().get("ref");
                if (ref != null) {
                    properties.setProperty(prefix + ".appenderRef." + ref + ".ref", ref);
                }
            }
        }
    }
}
