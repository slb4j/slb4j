package org.slb4j.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
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

    @Benchmark
    public void slf4j() {
        slf4jLogger.info(MESSAGE);
    }

    @Benchmark
    public void log4j() {
        log4jLogger.info(MESSAGE);
    }

    @Benchmark
    public void jul() {
        julLogger.info(MESSAGE);
    }

    @Benchmark
    public void jcl() {
        jclLogger.info(MESSAGE);
    }
}
