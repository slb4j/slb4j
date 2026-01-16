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
}
