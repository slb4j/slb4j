package org.slb4j.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class JulBenchmark extends org.slb4j.benchmark.AbstractLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    @Param({"SIMPLE"})
    public String format;

    private org.slf4j.Logger slf4jLogger;
    private org.apache.logging.log4j.Logger log4jLogger;
    private Logger julLogger;
    private org.apache.commons.logging.Log jclLogger;
    
    private Path tempFile;
    private FileHandler fileHandler;

    @Override
    protected void setupLogging() throws IOException {
        this.category = category;
        this.format = format;
        tempFile = Files.createTempFile("jul-bench", ".log");
        
        LogManager.getLogManager().reset();
        julLogger = Logger.getLogger(JulBenchmark.class.getName());
        
        if ("FILE".equals(category)) {
            fileHandler = new FileHandler(tempFile.toString());
            fileHandler.setFormatter(new SimpleFormatter());
            julLogger.addHandler(fileHandler);
        } else {
            java.util.logging.ConsoleHandler consoleHandler = new java.util.logging.ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            julLogger.addHandler(consoleHandler);
        }

        slf4jLogger = LoggerFactory.getLogger(JulBenchmark.class);
        log4jLogger = org.apache.logging.log4j.LogManager.getLogger(JulBenchmark.class);
        jclLogger = org.apache.commons.logging.LogFactory.getLog(JulBenchmark.class);
    }

    @Override
    protected void tearDownLogging() {
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

    @Benchmark
    public void slf4j() {
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
