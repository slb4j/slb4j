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

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ParallelLoggingBenchmark {

    @Param({"slb4j", "log4j", "logback", "jul"})
    public String backend;

    private Path tempFile;
    private org.slb4j.handler.FileHandler slb4jFileHandler;
    private java.util.logging.FileHandler julFileHandler;
    
    protected org.slf4j.Logger slf4jLogger;
    protected org.apache.logging.log4j.Logger log4jLogger;

    private PrintStream originalOut;
    private PrintStream originalErr;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));

        tempFile = Files.createTempFile("parallel-bench-" + backend, ".log");

        switch (backend) {
            case "slb4j" -> setupSlb4j();
            case "log4j" -> setupLog4j();
            case "logback" -> setupLogback();
            case "jul" -> setupJul();
        }

        slf4jLogger = org.slf4j.LoggerFactory.getLogger(ParallelLoggingBenchmark.class);
        log4jLogger = org.apache.logging.log4j.LogManager.getLogger(ParallelLoggingBenchmark.class);
    }

    private void setupSlb4j() throws IOException {
        org.slb4j.SLB4J.init();
        org.slb4j.LogPattern pattern = org.slb4j.LogPattern.parse("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger - %msg%n");
        org.slb4j.dispatcher.UniversalDispatcher dispatcher = org.slb4j.dispatcher.UniversalDispatcher.getInstance();
        dispatcher.getLogHandlers().forEach(dispatcher::removeLogHandler);
        slb4jFileHandler = new org.slb4j.handler.FileHandler("file", tempFile, false);
        slb4jFileHandler.setPattern(pattern);
        dispatcher.addLogHandler(slb4jFileHandler);
    }

    private void setupLog4j() {
        System.setProperty("logFile", tempFile.toString());
        System.setProperty("log4j.configurationFile", "log4j2-bench.xml");
        org.apache.logging.log4j.core.config.Configurator.reconfigure();
    }

    private void setupLogback() throws IOException {
        ch.qos.logback.classic.LoggerContext context = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        context.reset();
        ch.qos.logback.classic.encoder.PatternLayoutEncoder encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger - %msg%n");
        encoder.start();
        ch.qos.logback.core.FileAppender fileAppender = new ch.qos.logback.core.FileAppender();
        fileAppender.setFile(tempFile.toString());
        fileAppender.setEncoder(encoder);
        fileAppender.setContext(context);
        fileAppender.setName("Appender");
        fileAppender.start();
        ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();
        root.addAppender(fileAppender);
        root.setLevel(ch.qos.logback.classic.Level.INFO);
    }

    private void setupJul() throws IOException {
        // Configure JUL Backend
        java.util.logging.LogManager manager = java.util.logging.LogManager.getLogManager();
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");

        for (java.util.logging.Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
        root.setLevel(java.util.logging.Level.INFO);

        // Explicitly enable the benchmark class logger in JUL
        java.util.logging.Logger benchmarkLogger = java.util.logging.Logger.getLogger(ParallelLoggingBenchmark.class.getName());
        benchmarkLogger.setLevel(java.util.logging.Level.INFO);

        // Setup Handler
        julFileHandler = new java.util.logging.FileHandler(tempFile.toString());
        julFileHandler.setFormatter(new java.util.logging.SimpleFormatter());
        julFileHandler.setLevel(java.util.logging.Level.INFO);
        root.addHandler(julFileHandler);

        // Force-check/nudge Log4j bridge level
        org.apache.logging.log4j.core.config.Configurator.setLevel(
                ParallelLoggingBenchmark.class.getName(),
                org.apache.logging.log4j.Level.INFO
        );
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        switch (backend) {
            case "slb4j" -> {
                if (slb4jFileHandler != null) slb4jFileHandler.close();
            }
            case "log4j" -> org.apache.logging.log4j.LogManager.shutdown();
            case "logback" -> ((ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory()).stop();
            case "jul" -> {
                if (julFileHandler != null) julFileHandler.close();
            }
        }

        System.setOut(originalOut);
        System.setErr(originalErr);
        java.nio.file.Files.deleteIfExists(tempFile);
    }

    @Benchmark
    @Threads(1)
    public void slf4j_1() {
        slf4j();
    }

    @Benchmark
    @Threads(2)
    public void slf4j_2() {
        slf4j();
    }

    @Benchmark
    @Threads(4)
    public void slf4j_4() {
        slf4j();
    }

    @Benchmark
    @Threads(8)
    public void slf4j_8() {
        slf4j();
    }

    @Benchmark
    @Threads(16)
    public void slf4j_16() {
        slf4j();
    }

    @Benchmark
    @Threads(64)
    public void slf4j_64() {
        slf4j();
    }

    @Benchmark
    @Threads(128)
    public void slf4j_128() {
        slf4j();
    }

    @Benchmark
    @Threads(1)
    public void log4j_1() {
        log4j();
    }

    @Benchmark
    @Threads(2)
    public void log4j_2() {
        log4j();
    }

    @Benchmark
    @Threads(4)
    public void log4j_4() {
        log4j();
    }

    @Benchmark
    @Threads(8)
    public void log4j_8() {
        log4j();
    }

    @Benchmark
    @Threads(16)
    public void log4j_16() {
        log4j();
    }

    @Benchmark
    @Threads(64)
    public void log4j_64() {
        log4j();
    }

    @Benchmark
    @Threads(128)
    public void log4j_128() {
        log4j();
    }

    private void slf4j() {
        if (slf4jLogger.isInfoEnabled()) {
            slf4jLogger.info("Parallel benchmark slf4j backend={}", backend);
        }
    }

    private void log4j() {
        log4jLogger.info(() -> "Parallel benchmark log4j backend=" + backend);
    }
}
