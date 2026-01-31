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

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.slb4j.LogLayout;
import org.slb4j.SLB4J;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.FileHandler;
import org.slb4j.layout.PatternLayout;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Slb4jBenchmark extends AbstractLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    @Param({"COMPACT", "DEFAULT", "DETAILED"})
    public String format;

    private Path tempFile;
    private FileHandler fileHandler;


    @Override
    public String backend() {
        return "slb4j";
    }

    @Override
    protected void setupLogging() throws IOException {
        SLB4J.init();

        tempFile = Files.createTempFile("slb4j-bench", ".log");

        LogLayout pattern = switch (format) {
            case "COMPACT" -> PatternLayout.COMPACT_PATTERN;
            case "DETAILED" -> PatternLayout.DETAILED_PATTERN;
            default -> PatternLayout.DEFAULT_PATTERN;
        };

        UniversalDispatcher dispatcher = UniversalDispatcher.getInstance();
        dispatcher.getLogHandlers().forEach(dispatcher::removeLogHandler);

        if ("CONSOLE".equals(category)) {
            ConsoleHandler consoleHandler = new ConsoleHandler("console", System.out, !"DETAILED".equals(format));
            consoleHandler.setLayout(pattern);
            dispatcher.addLogHandler(consoleHandler);
        } else {
            fileHandler = new FileHandler("file", tempFile, false);
            fileHandler.setLayout(pattern);
            dispatcher.addLogHandler(fileHandler);
        }

        slf4jLogger = LoggerFactory.getLogger(Slb4jBenchmark.class);
        log4jLogger = org.apache.logging.log4j.LogManager.getLogger(Slb4jBenchmark.class);
        julLogger = java.util.logging.Logger.getLogger(Slb4jBenchmark.class.getName());
        jclLogger = org.apache.commons.logging.LogFactory.getLog(Slb4jBenchmark.class);

        if ("DETAILED".equals(format)) {
            org.slf4j.MDC.put("userId", "benchUser");
        }
    }

    @Override
    protected void tearDownLogging() {
        org.slf4j.MDC.clear();
        if (fileHandler != null) {
            fileHandler.close();
        }
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
