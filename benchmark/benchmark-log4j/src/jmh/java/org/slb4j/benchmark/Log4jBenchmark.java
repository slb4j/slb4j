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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Log4jBenchmark extends org.slb4j.benchmark.AbstractLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    @Param({"COMPACT", "DEFAULT", "DETAILED"})
    public String format;

    private Path tempFile;

    @Override
    public String backend() {
        return "log4j";
    }

    @Override
    protected void setupLogging() throws IOException {
        tempFile = Files.createTempFile("log4j-bench", ".log");

        org.apache.logging.log4j.core.LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

        String pattern = switch (format) {
            case "COMPACT" -> "%d{HH:mm:ss.SSS} %-5p %-30.30c{1.} - %m%n";
            case "DETAILED" -> "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5p %X{userId} (%C.%M(%F:%L)) - %m%n";
            default -> "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p %c - %m%n";
        };

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
        org.apache.logging.log4j.core.config.AppenderRef ref = org.apache.logging.log4j.core.config.AppenderRef.createAppenderRef(appender.getName(), null, null);
        org.apache.logging.log4j.core.config.AppenderRef[] refs = new org.apache.logging.log4j.core.config.AppenderRef[]{ref};

        org.apache.logging.log4j.core.config.LoggerConfig loggerConfig = config.getRootLogger();
        loggerConfig.getAppenders().forEach((name, a) -> loggerConfig.removeAppender(name));
        loggerConfig.addAppender(appender, null, null);
        loggerConfig.setLevel(org.apache.logging.log4j.Level.INFO);

        context.updateLoggers();

        slf4jLogger = LoggerFactory.getLogger(Log4jBenchmark.class);
        log4jLogger = LogManager.getLogger(Log4jBenchmark.class);
        julLogger = java.util.logging.Logger.getLogger(Log4jBenchmark.class.getName());
        jclLogger = org.apache.commons.logging.LogFactory.getLog(Log4jBenchmark.class);

        slf4jMarker = MarkerFactory.getMarker("BENCH");
        log4jMarker = org.apache.logging.log4j.MarkerManager.getMarker("BENCH");

        if ("DETAILED".equals(format)) {
            org.slf4j.MDC.put("userId", "benchUser");
            org.apache.logging.log4j.ThreadContext.put("userId", "benchUser");
        }
    }

    @Override
    protected void tearDownLogging() {
        org.slf4j.MDC.clear();
        org.apache.logging.log4j.ThreadContext.clearAll();
        LogManager.shutdown();
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Setup(Level.Iteration)
    public void setupIteration(org.openjdk.jmh.infra.BenchmarkParams params) {
        updateLogMessage(params, category, format);
    }
}
