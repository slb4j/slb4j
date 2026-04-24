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

package org.slb4j.config.yaml;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import org.slb4j.LoggingConfiguration;
import org.slb4j.config.ConfigParser;
import org.slb4j.config.ConfigParserLog4jProperties;
import org.slb4j.config.support.Log4j2TreeFlattener;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * YAML configuration parser for SLB4J.
 */
public class ConfigParserLog4jYaml implements ConfigParser {

    /**
     * Constructs a new {@code ConfigParserLog4jYaml}.
     */
    public ConfigParserLog4jYaml() {
        // nothing to do
    }

    private final ObjectMapper mapper = new YAMLMapper();

    @Override
    public LoggingConfiguration parse(InputStream in) throws IOException {
        JsonNode root = mapper.readTree(in);
        // Log4j2 YAML usually has a root "Configuration" object
        JsonNode config = root.get("Configuration");
        if (config == null) {
            config = root;
        }

        Log4j2TreeFlattener flattener = new Log4j2TreeFlattener();
        Properties properties = flattener.flatten(new JacksonNode(config, "Configuration"));

        return new ConfigParserLog4jProperties().parse(properties);
    }

    private static class JacksonNode implements Log4j2TreeFlattener.Node {
        private final JsonNode node;
        private final String name;

        JacksonNode(JsonNode node, String name) {
            this.node = node;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, String> getAttributes() {
            Map<String, String> attributes = new HashMap<>();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (field.getValue().isValueNode()) {
                    attributes.put(field.getKey(), field.getValue().asString());
                }
            }
            return attributes;
        }

        @Override
        public List<Log4j2TreeFlattener.Node> getChildren() {
            List<Log4j2TreeFlattener.Node> children = new ArrayList<>();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                JsonNode value = field.getValue();
                if (value.isObject()) {
                    children.add(new JacksonNode(value, field.getKey()));
                } else if (value.isArray()) {
                    for (JsonNode item : value) {
                        if (item.isObject()) {
                            children.add(new JacksonNode(item, field.getKey()));
                        }
                    }
                }
            }
            return children;
        }

        @Override
        public String getTextValue() {
             return node.isValueNode() ? node.asString() : null;
        }
    }
}
