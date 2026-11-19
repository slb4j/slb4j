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

import java.nio.file.Path;

public final class Log4jConfigurator implements BenchmarkConfigurator {

    private final String category;
    private final String format;
    private final Path outputFile;

    public Log4jConfigurator(String category, String format, Path outputFile) {
        this.category = category;
        this.format = format;
        this.outputFile = outputFile;
    }

    @Override
    public void setup() {
        org.apache.logging.log4j.core.LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
        org.apache.logging.log4j.core.layout.PatternLayout layout = org.apache.logging.log4j.core.layout.PatternLayout.newBuilder()
                .setPattern(AbstractLoggingBenchmark.getPattern(format))
                .build();

        org.apache.logging.log4j.core.Appender appender;
        if ("FILE".equals(category)) {
            appender = org.apache.logging.log4j.core.appender.FileAppender.newBuilder()
                    .setName("File")
                    .setFileName(outputFile.toString())
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
        org.apache.logging.log4j.core.config.LoggerConfig root = config.getRootLogger();
        root.getAppenders().forEach((name, existing) -> root.removeAppender(name));
        root.addAppender(appender, null, null);
        root.setLevel(org.apache.logging.log4j.Level.INFO);
        context.updateLoggers();
    }

    @Override
    public void tearDown() {
        LogManager.shutdown();
    }
}