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

import org.junit.jupiter.api.Test;
import org.slb4j.LogLevel;
import org.slb4j.LoggingConfiguration;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigParserLog4jYamlTest {

    @Test
    void testParse() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/log4j2-test-yaml.yaml")) {
            assertNotNull(in);
            ConfigParserLog4jYaml parser = new ConfigParserLog4jYaml();
            LoggingConfiguration config = parser.parse(in);

            assertNotNull(config);
            assertEquals(LogLevel.INFO, config.getRootLevel());
            assertNotNull(config.getHandler("STDOUT"));
            assertNotNull(config.getHandler("STDOUT").getFilter());
            assertEquals(LogLevel.DEBUG, config.getRootFilter().getLevel("org.slb4j.test"));
        }
    }
}
