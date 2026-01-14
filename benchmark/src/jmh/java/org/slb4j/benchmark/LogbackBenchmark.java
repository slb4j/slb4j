package org.slb4j.benchmark;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
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

public class LogbackBenchmark extends org.slb4j.benchmark.AbstractLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    @Param({"SIMPLE", "MDC", "MARKER", "LOCATION", "COLOR"})
    public String format;

    private org.slf4j.Logger slf4jLogger;
    private org.apache.logging.log4j.Logger log4jLogger;
    private java.util.logging.Logger julLogger;
    private org.apache.commons.logging.Log jclLogger;
    
    private Marker marker;
    private Path tempFile;

    @Override
    protected void setupLogging() throws IOException {
        this.category = category;
        this.format = format;
        tempFile = Files.createTempFile("logback-bench", ".log");
        
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            context.putProperty("logFile", tempFile.toString());
            // Note: For simplicity, we use one config. Real benchmark would vary pattern.
            configurator.doConfigure(getClass().getResource("/logback-bench.xml"));
        } catch (JoranException je) {
            // StatusPrinter will handle this
        }

        slf4jLogger = LoggerFactory.getLogger(LogbackBenchmark.class);
        log4jLogger = org.apache.logging.log4j.LogManager.getLogger(LogbackBenchmark.class);
        julLogger = java.util.logging.Logger.getLogger(LogbackBenchmark.class.getName());
        jclLogger = org.apache.commons.logging.LogFactory.getLog(LogbackBenchmark.class);
        
        marker = MarkerFactory.getMarker("BENCH");
        if ("MDC".equals(format)) {
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

    @Benchmark
    public void slf4j() {
        if ("MARKER".equals(format)) {
            switch (messageType) {
                case "CONSTANT" -> slf4jLogger.info(marker, logMessage);
                case "ARGUMENTS" -> slf4jLogger.info(marker, "Benchmark backend={} frontend={} category={} format={} messageType={}", backend, "slf4j", category, format, messageType);
                case "LAMBDA" -> {
                    if (slf4jLogger.isInfoEnabled()) {
                        slf4jLogger.info(marker, String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend, "slf4j", category, format, messageType));
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
        switch (messageType) {
            case "CONSTANT" -> log4jLogger.info(logMessage);
            case "ARGUMENTS" -> log4jLogger.info("Benchmark backend={} frontend={} category={} format={} messageType={}", backend, "log4j", category, format, messageType);
            case "LAMBDA" -> log4jLogger.info(() -> String.format("Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s", backend, "log4j", category, format, messageType));
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
