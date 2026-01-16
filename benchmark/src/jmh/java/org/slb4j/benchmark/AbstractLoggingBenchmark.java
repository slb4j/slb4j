package org.slb4j.benchmark;

import org.apache.logging.log4j.Logger;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Marker;

import java.util.concurrent.TimeUnit;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.IOException;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public abstract class AbstractLoggingBenchmark {

    @Param({"false", "true"})
    public String outputToFile;

    @Param({"slb4j", "log4j", "logback", "jul"})
    public String backend;

    @Param({"CONSTANT", "ARGUMENTS", "LAMBDA"})
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

    @Setup(Level.Trial)
    public void setup() throws IOException {
        originalOut = System.out;
        originalErr = System.err;
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
                backend, frontend, category, format, messageType);
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
                case "ARGUMENTS" -> slf4jLogger.info(slf4jMarker, "Benchmark backend={} frontend={} category={} format={} messageType={}", backend, "slf4j", category, format, messageType);
                case "LAMBDA" -> {
                    if (slf4jLogger.isInfoEnabled()) {
                        slf4jLogger.info(slf4jMarker, String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend, "slf4j", category, format, messageType));
                    }
                }
            }
        } else {
            switch (messageType) {
                case "CONSTANT" -> slf4jLogger.info(logMessage);
                case "ARGUMENTS" -> slf4jLogger.info("Benchmark backend={} frontend={} category={} format={} messageType={}", backend, "slf4j", category, format, messageType);
                case "LAMBDA" -> {
                    if (slf4jLogger.isInfoEnabled()) {
                        slf4jLogger.info(String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend, "slf4j", category, format, messageType));
                    }
                }
            }
        }
    }

    @Benchmark
    public void log4j() {
        if ("MARKER".equals(format)) {
            switch (messageType) {
                case "CONSTANT" -> log4jLogger.info(log4jMarker, logMessage);
                case "ARGUMENTS" -> log4jLogger.info(log4jMarker, "Benchmark backend={} frontend={} category={} format={} messageType={}", backend, "log4j", category, format, messageType);
                case "LAMBDA" -> log4jLogger.info(log4jMarker, () -> String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend, "log4j", category, format, messageType));
            }
        } else {
            switch (messageType) {
                case "CONSTANT" -> log4jLogger.info(logMessage);
                case "ARGUMENTS" -> log4jLogger.info("Benchmark backend={} frontend={} category={} format={} messageType={}", backend, "log4j", category, format, messageType);
                case "LAMBDA" -> log4jLogger.info(() -> String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend, "log4j", category, format, messageType));
            }
        }
    }

    @Benchmark
    public void jul() {
        switch (messageType) {
            case "CONSTANT" -> julLogger.info(logMessage);
            case "ARGUMENTS" -> julLogger.log(java.util.logging.Level.INFO, "Benchmark backend={0} frontend={1} category={2} format={3} messageType={4}", new Object[]{backend, "jul", category, format, messageType});
            case "LAMBDA" -> julLogger.info(() -> String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend, "jul", category, format, messageType));
        }
    }

    @Benchmark
    public void jcl() {
        switch (messageType) {
            case "CONSTANT" -> jclLogger.info(logMessage);
            case "ARGUMENTS", "LAMBDA" -> jclLogger.info(String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend, "jcl", category, format, messageType));
        }
    }
}
