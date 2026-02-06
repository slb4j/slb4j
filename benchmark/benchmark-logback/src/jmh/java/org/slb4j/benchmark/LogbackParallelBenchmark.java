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
import org.openjdk.jmh.annotations.Param;
import org.slf4j.LoggerFactory;

public class LogbackParallelBenchmark extends ParallelLoggingBenchmark {
    @Param({"CONSOLE", "FILE"})
    public String category;

    @Override
    public String backend() {
        return "logback";
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public void setupBackend() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger - %msg%n";

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
    }
}
