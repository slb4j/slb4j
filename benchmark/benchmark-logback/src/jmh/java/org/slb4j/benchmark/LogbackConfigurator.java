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
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class LogbackConfigurator implements BenchmarkConfigurator {

    private final String category;
    private final String format;
    private final Path outputFile;

    public LogbackConfigurator(String category, String format, Path outputFile) {
        this.category = category;
        this.format = format;
        this.outputFile = outputFile;
    }

    @Override
    public void setup() {
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
        org.slf4j.bridge.SLF4JBridgeHandler.install();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        ch.qos.logback.classic.encoder.PatternLayoutEncoder encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(AbstractLoggingBenchmark.getPattern(format).replace("(", "\\(").replace(")", "\\)"));
        encoder.start();

        ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender;
        if ("FILE".equals(category)) {
            ch.qos.logback.core.FileAppender<ch.qos.logback.classic.spi.ILoggingEvent> fileAppender = new ch.qos.logback.core.FileAppender<>();
            fileAppender.setFile(outputFile.toString());
            fileAppender.setEncoder(encoder);
            appender = fileAppender;
        } else {
            ch.qos.logback.core.ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent> consoleAppender = new ch.qos.logback.core.ConsoleAppender<>();
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
    }

    @Override
    public void tearDown() {
        org.slf4j.bridge.SLF4JBridgeHandler.uninstall();
        ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
    }
}