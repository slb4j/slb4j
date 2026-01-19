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

import org.apache.logging.log4j.Logger;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Marker;

import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.IOException;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public abstract class AbstractLoggingBenchmark {

    public String outputToFile = "false";

    @Param({"CONSTANT", "ARGUMENTS", "MESSAGE_SUPPLIER", "LAMBDA_PARAMETER"})
    public String messageType;

    public String category; // Will be set in subclasses
    public String format;   // Will be set in subclasses

    protected String logMessage;

    protected PrintStream originalOut;
    protected PrintStream originalErr;
    protected FileOutputStream fileOut;

    protected org.slf4j.Logger slf4jLogger;
    protected Logger log4jLogger;
    protected java.util.logging.Logger julLogger;
    protected org.apache.commons.logging.Log jclLogger;

    protected Marker slf4jMarker;
    protected org.apache.logging.log4j.Marker log4jMarker;

    public abstract String backend();

    @Setup(Level.Trial)
    public void setup(org.openjdk.jmh.infra.BenchmarkParams params) throws IOException {
        originalOut = System.out;
        originalErr = System.err;

        // Print testing info only once per fork/trial
        String benchmarkName = params.getBenchmark();
        String frontend = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);
        originalOut.println("Testing " + backend() + "-" + frontend + " ...");

        if ("true".equals(outputToFile)) {
            fileOut = new FileOutputStream("benchmark.out", true);
            PrintStream ps = new PrintStream(fileOut);
            System.setOut(ps);
            System.setErr(ps);
        } else {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        }
        setupLogging();
    }

    protected void updateLogMessage(org.openjdk.jmh.infra.BenchmarkParams params, String category, String format) {
        String benchmarkName = params.getBenchmark();
        String frontend = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);
        logMessage = String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s",
                backend(), frontend, category, format, messageType);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        System.setOut(originalOut);
        System.setErr(originalErr);
        if (fileOut != null) {
            fileOut.close();
        }
        tearDownLogging();
    }

    protected abstract void setupLogging() throws IOException;

    protected abstract void tearDownLogging();


    @Benchmark
    public void slf4j() {
        if ("MARKER".equals(format)) {
            switch (messageType) {
                case "CONSTANT" -> slf4jLogger.info(slf4jMarker, logMessage);
                case "ARGUMENTS" ->
                        slf4jLogger.info(slf4jMarker, "Benchmark backend={} frontend={} category={} format={} messageType={}", backend(), "slf4j", category, format, messageType);
                case "MESSAGE_SUPPLIER" ->
                        slf4jLogger.atInfo().addMarker(slf4jMarker).log(() -> String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend(), "slf4j", category, format, messageType));
                case "LAMBDA_PARAMETER" ->
                        slf4jLogger.atInfo().addMarker(slf4jMarker).addArgument(this::backend).addArgument("slf4j").addArgument(category).addArgument(format).addArgument(() -> messageType).log("Benchmark backend={} frontend={} category={} format={} messageType={}");
            }
        } else {
            switch (messageType) {
                case "CONSTANT" -> slf4jLogger.info(logMessage);
                case "ARGUMENTS" -> slf4jLogger.info("Benchmark backend={} frontend={} category={} format={} messageType={}", backend(), "slf4j", category, format, messageType);
                case "MESSAGE_SUPPLIER" ->
                        slf4jLogger.atInfo().log(() -> String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend(), "slf4j", category, format, messageType));
                case "LAMBDA_PARAMETER" ->
                        slf4jLogger.atInfo().addArgument(this::backend).addArgument("slf4j").addArgument(category).addArgument(format).addArgument(() -> messageType).log("Benchmark backend={} frontend={} category={} format={} messageType={}");
            }
        }
    }

    @Benchmark
    public void log4j() {
        if ("MARKER".equals(format)) {
            switch (messageType) {
                case "CONSTANT" -> log4jLogger.info(log4jMarker, logMessage);
                case "ARGUMENTS" -> log4jLogger.info(log4jMarker, "Benchmark backend={} frontend={} category={} format={} messageType={}", backend(), "log4j", category, format, messageType);
                case "MESSAGE_SUPPLIER" -> log4jLogger.info(log4jMarker, () -> logMessage);
                case "LAMBDA_PARAMETER" -> log4jLogger.info(log4jMarker, "Benchmark backend={} frontend={} category={} format={} messageType={}", backend(), "log4j", category, format, (Supplier<String>) () -> messageType);
            }
        } else {
            switch (messageType) {
                case "CONSTANT" -> log4jLogger.info(logMessage);
                case "ARGUMENTS" -> log4jLogger.info("Benchmark backend={} frontend={} category={} format={} messageType={}", backend(), "log4j", category, format, messageType);
                case "MESSAGE_SUPPLIER" -> log4jLogger.info(() -> logMessage);
                case "LAMBDA_PARAMETER" -> log4jLogger.info("Benchmark backend={} frontend={} category={} format={} messageType={}", backend(), "log4j", category, format, (Supplier<String>) () -> messageType);
            }
        }
    }

    @Benchmark
    public void jul() {
        switch (messageType) {
            case "CONSTANT" -> julLogger.info(logMessage);
            case "ARGUMENTS" -> julLogger.log(java.util.logging.Level.INFO, "Benchmark backend={0} frontend={1} category={2} format={3} messageType={4}", new Object[]{backend(), "jul", category, format, messageType});
            case "MESSAGE_SUPPLIER" -> julLogger.info(() -> String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend(), "jul", category, format, messageType));
            default -> { assert false : "unsupported message type for JUL: " + messageType; }
        }
    }

    @Benchmark
    public void jcl() {
        assert "CONSTANT".equals(messageType) : "unsupported message type for JCL: " + messageType;
        jclLogger.info(logMessage);
    }
}
