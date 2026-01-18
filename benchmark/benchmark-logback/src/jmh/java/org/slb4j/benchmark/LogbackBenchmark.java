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

import ch.qos.logback.classic.LoggerContext;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LogbackBenchmark extends org.slb4j.benchmark.AbstractLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    @Param({"COMPACT", "DEFAULT", "DETAILED"})
    public String format;

    private Path tempFile;


    @Override
    public String backend() {
        return "logback";
    }

    @Override
    protected void setupLogging() throws IOException {
        tempFile = Files.createTempFile("logback-bench", ".log");

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        String pattern = switch (format) {
            case "COMPACT" -> "%d{HH:mm:ss.SSS} %-5level %-30.30logger{0} - %msg%n";
            case "DETAILED" -> "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %X{userId} (%class.%method\\(%file:%line\\)) - %msg%n";
            default -> "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger - %msg%n";
        };

        ch.qos.logback.classic.encoder.PatternLayoutEncoder encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.start();

        ch.qos.logback.core.Appender appender;
        if ("FILE".equals(category)) {
            ch.qos.logback.core.FileAppender fileAppender = new ch.qos.logback.core.FileAppender();
            fileAppender.setFile(tempFile.toString());
            fileAppender.setEncoder(encoder);
            appender = fileAppender;
        } else {
            ch.qos.logback.core.ConsoleAppender consoleAppender = new ch.qos.logback.core.ConsoleAppender();
            consoleAppender.setEncoder(encoder);
            appender = consoleAppender;
        }
        appender.setContext(context);
        appender.setName("Appender");
        appender.start();

        ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();
        root.addAppender(appender);
        root.setLevel(ch.qos.logback.classic.Level.INFO);

        slf4jLogger = LoggerFactory.getLogger(LogbackBenchmark.class);
        log4jLogger = org.apache.logging.log4j.LogManager.getLogger(LogbackBenchmark.class);
        julLogger = java.util.logging.Logger.getLogger(LogbackBenchmark.class.getName());
        jclLogger = org.apache.commons.logging.LogFactory.getLog(LogbackBenchmark.class);

        if ("DETAILED".equals(format)) {
            org.slf4j.MDC.put("userId", "benchUser");
        }
    }

    @Override
    protected void tearDownLogging() {
        org.slf4j.MDC.clear();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.stop();
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
