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
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Log4jBenchmark extends org.slb4j.benchmark.AbstractLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    @Param({"SIMPLE", "MDC", "MARKER", "LOCATION", "COLOR"})
    public String format;

    private Path tempFile;

    @Override
    protected void setupLogging() throws IOException {
        tempFile = Files.createTempFile("log4j-bench", ".log");
        System.setProperty("logFile", tempFile.toString());
        System.setProperty("log4j.configurationFile", "log4j2-bench.xml");

        // Note: For simplicity, we are using a single config file and the parameters are not fully reflected 
        // in Log4j2 config yet. In a real benchmark, we should programmatically configure Log4j2.

        // Ensure Log4j2 is initialized
        Configurator.reconfigure();

        slf4jLogger = LoggerFactory.getLogger(Log4jBenchmark.class);
        log4jLogger = LogManager.getLogger(Log4jBenchmark.class);
        julLogger = java.util.logging.Logger.getLogger(Log4jBenchmark.class.getName());
        jclLogger = org.apache.commons.logging.LogFactory.getLog(Log4jBenchmark.class);

        slf4jMarker = MarkerFactory.getMarker("BENCH");
        log4jMarker = org.apache.logging.log4j.MarkerManager.getMarker("BENCH");

        if ("MDC".equals(format)) {
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
