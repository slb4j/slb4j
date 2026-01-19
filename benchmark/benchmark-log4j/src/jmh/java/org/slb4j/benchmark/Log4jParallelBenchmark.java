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
package org.slb4j.benchmark;

import org.apache.logging.log4j.LogManager;
import org.openjdk.jmh.annotations.Param;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Log4jParallelBenchmark extends ParallelLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    private Path tempFile;

    @Override
    public String backend() {
        return "log4j";
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    protected void setupLogging() throws IOException {
        tempFile = Files.createTempFile("log4j-parallel-bench", ".log");

        org.apache.logging.log4j.core.LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

        String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p %c - %m%n";

        org.apache.logging.log4j.core.layout.PatternLayout layout = org.apache.logging.log4j.core.layout.PatternLayout.newBuilder()
                .withPattern(pattern)
                .build();

        org.apache.logging.log4j.core.Appender appender;
        if ("FILE".equals(category)) {
            appender = org.apache.logging.log4j.core.appender.FileAppender.newBuilder()
                    .setName("File")
                    .withFileName(tempFile.toString())
                    .setLayout(layout)
                    .build();
        } else {
            appender = org.apache.logging.log4j.core.appender.ConsoleAppender.newBuilder()
                    .setName("Console")
                    .setTarget(org.apache.logging.log4j.core.appender.ConsoleAppender.Target.SYSTEM_OUT)
                    .setLayout(layout)
                    .build();
        }

        appender.start();
        config.addAppender(appender);

        org.apache.logging.log4j.core.config.LoggerConfig loggerConfig = config.getRootLogger();
        loggerConfig.getAppenders().forEach((name, a) -> loggerConfig.removeAppender(name));
        loggerConfig.addAppender(appender, null, null);
        loggerConfig.setLevel(org.apache.logging.log4j.Level.INFO);

        context.updateLoggers();

        slf4jLogger = LoggerFactory.getLogger(Log4jParallelBenchmark.class);
        log4jLogger = LogManager.getLogger(Log4jParallelBenchmark.class);
    }

    @Override
    protected void tearDownLogging() {
        LogManager.shutdown();
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
