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

    private org.slf4j.Logger slf4jLogger;
    private Logger log4jLogger;
    private java.util.logging.Logger julLogger;
    private org.apache.commons.logging.Log jclLogger;
    
    private Marker slf4jMarker;
    private org.apache.logging.log4j.Marker log4jMarker;
    private Path tempFile;

    @Override
    protected void setupLogging() throws IOException {
        this.category = category;
        this.format = format;
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
