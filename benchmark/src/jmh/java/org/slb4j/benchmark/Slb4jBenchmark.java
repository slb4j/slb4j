package org.slb4j.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.slb4j.LogPattern;
import org.slb4j.SLB4J;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.FileHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Slb4jBenchmark extends AbstractLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    @Param({"SIMPLE", "MDC", "MARKER", "LOCATION", "COLOR"})
    public String format;

    private Logger slf4jLogger;
    private org.apache.logging.log4j.Logger log4jLogger;
    private java.util.logging.Logger julLogger;
    private org.apache.commons.logging.Log jclLogger;
    
    private Marker marker;
    private Path tempFile;
    private FileHandler fileHandler;

    @Override
    protected void setupLogging() throws IOException {
        this.category = category;
        this.format = format;
        SLB4J.init();

        tempFile = Files.createTempFile("slb4j-bench", ".log");
        
        LogPattern pattern = switch (format) {
            case "MDC" -> LogPattern.parse("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger [%X{userId}] - %msg%n");
            case "MARKER" -> LogPattern.parse("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger (%marker) - %msg%n");
            case "LOCATION" -> LogPattern.parse("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger (%file:%line) - %msg%n");
            case "COLOR" -> LogPattern.parse("%Cstart%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger - %msg%Cend%n");
            default -> LogPattern.parse("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger - %msg%n");
        };

        UniversalDispatcher dispatcher = UniversalDispatcher.getInstance();
        dispatcher.getLogHandlers().forEach(dispatcher::removeLogHandler);

        if ("CONSOLE".equals(category)) {
            ConsoleHandler consoleHandler = new ConsoleHandler("console", System.out, "COLOR".equals(format));
            consoleHandler.setPattern(pattern);
            dispatcher.addLogHandler(consoleHandler);
        } else {
            fileHandler = new FileHandler("file", tempFile, false);
            fileHandler.setPattern(pattern);
            dispatcher.addLogHandler(fileHandler);
        }

        slf4jLogger = LoggerFactory.getLogger(Slb4jBenchmark.class);
        log4jLogger = org.apache.logging.log4j.LogManager.getLogger(Slb4jBenchmark.class);
        julLogger = java.util.logging.Logger.getLogger(Slb4jBenchmark.class.getName());
        jclLogger = org.apache.commons.logging.LogFactory.getLog(Slb4jBenchmark.class);
        
        marker = MarkerFactory.getMarker("BENCH");
        if ("MDC".equals(format)) {
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

    @Benchmark
    public void slf4j() {
        if ("MARKER".equals(format)) {
            slf4jLogger.info(marker, logMessage);
        } else {
            slf4jLogger.info(logMessage);
        }
    }

    @Benchmark
    public void log4j() {
        log4jLogger.info(logMessage);
    }

    @Benchmark
    public void jul() {
        julLogger.info(logMessage);
    }

    @Benchmark
    public void jcl() {
        jclLogger.info(logMessage);
    }
}
