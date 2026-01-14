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
