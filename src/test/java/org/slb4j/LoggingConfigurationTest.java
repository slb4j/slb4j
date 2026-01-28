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

import org.slb4j.handler.RotatingFileHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class LoggingConfigurationTest {

    @Test
    void testFileHandlerConfiguration(@TempDir Path tempDir) {
        Path logFile = tempDir.resolve("test.log");
        Properties props = new Properties();
        props.setProperty("appender.file.type", "File");
        props.setProperty("appender.file.fileName", logFile.toString());
        props.setProperty("appender.file.append", "false");
        props.setProperty("appender.file.filePattern", "test-%i.log");
        props.setProperty("appender.file.policies.size.size", "1024");
        props.setProperty("appender.file.policies.time.interval", "1");
        props.setProperty("appender.file.strategy.max", "5");
        props.setProperty("appender.file.layout.pattern", "%m%n");

        LoggingConfiguration config = LoggingConfiguration.parse(props);

        LogHandler handler = config.getHandlers().stream()
                .filter(h -> "file".equals(h.name()))
                .findFirst()
                .orElseThrow();

        assertInstanceOf(RotatingFileHandler.class, handler);
        RotatingFileHandler fileHandler = (RotatingFileHandler) handler;
        
        assertEquals(logFile.toAbsolutePath(), fileHandler.getPath().toAbsolutePath());
        assertFalse(fileHandler.isAppend());
        assertEquals("test-%i.log", fileHandler.getFilePattern());
        assertEquals(1024L, fileHandler.getMaxFileSize());
        assertNotNull(fileHandler.getRotationTimeUnit());
        assertEquals(5, fileHandler.getMaxBackupIndex());
        assertEquals("%m%n", fileHandler.getPattern().getPattern());

        // Test addToProperties
        Properties outProps = new Properties();
        config.addToProperties(outProps);

        assertEquals("RollingFile", outProps.getProperty("appender.file.type"));
        assertEquals(logFile.toString(), outProps.getProperty("appender.file.fileName"));
        assertEquals("false", outProps.getProperty("appender.file.append"));
        assertEquals("1024", outProps.getProperty("appender.file.policies.size.size"));
        assertEquals("5", outProps.getProperty("appender.file.strategy.max"));
        assertEquals("%m%n", outProps.getProperty("appender.file.layout.pattern"));
    }

    @Test
    void testParseJul() {
        Properties props = new Properties();
        props.setProperty("handlers", "java.util.logging.ConsoleHandler, java.util.logging.FileHandler");
        props.setProperty(".level", "INFO");
        props.setProperty("org.slb4j.level", "FINE"); // FINE should map to DEBUG
        props.setProperty("java.util.logging.FileHandler.pattern", "test.log");
        props.setProperty("java.util.logging.FileHandler.limit", "1024");
        props.setProperty("java.util.logging.FileHandler.count", "3");
        props.setProperty("java.util.logging.FileHandler.append", "true");

        LoggingConfiguration config = LoggingConfiguration.parseJul(props);

        // Check handlers
        assertTrue(config.getHandlers().stream().anyMatch(h -> "console".equals(h.name())));
        LogHandler fileHandler = config.getHandlers().stream()
                .filter(h -> "file".equals(h.name()))
                .findFirst()
                .orElseThrow();
        assertInstanceOf(RotatingFileHandler.class, fileHandler);
        RotatingFileHandler rotatingFileHandler = (RotatingFileHandler) fileHandler;
        assertEquals("test.log", rotatingFileHandler.getPath().toString());
        assertEquals(1024L, rotatingFileHandler.getMaxFileSize());
        assertEquals(2, rotatingFileHandler.getMaxBackupIndex()); // count 3 means 2 backups
        assertTrue(rotatingFileHandler.isAppend());

        // Check levels
        LogFilter filter = config.getFilters().iterator().next();
        assertTrue(filter.isEnabled("any.logger", LogLevel.INFO, null), "INFO should be enabled for any.logger");
        assertFalse(filter.isEnabled("any.logger", LogLevel.DEBUG, null), "DEBUG should NOT be enabled for any.logger");
        assertTrue(filter.isEnabled("org.slb4j.Test", LogLevel.DEBUG, null), "DEBUG should be enabled for org.slb4j.Test (mapped from FINE)");
    }
}
