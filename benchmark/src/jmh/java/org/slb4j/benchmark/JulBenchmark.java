package org.slb4j.benchmark;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

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

    @Param("SIMPLE")
    public String format;

    private Path tempFile;
    private FileHandler fileHandler;

    @Override
    protected void setupLogging() throws IOException {
        // 1. JCL Bridge Property (Must be first)
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Jdk14Logger");
        org.apache.commons.logging.LogFactory.releaseAll();

        // 2. Configure JUL Backend properly
        LogManager manager = LogManager.getLogManager();
        // Do NOT call manager.reset() - it kills the Log4j bridge listeners

        Logger root = Logger.getLogger("");
        // Clear existing handlers to prevent duplicates in JMH warmups
        for (java.util.logging.Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }

        root.setLevel(java.util.logging.Level.INFO);

        // 3. Explicitly enable the benchmark class logger in JUL
        // Bridges check this specific name!
        Logger benchmarkLogger = Logger.getLogger(JulBenchmark.class.getName());
        benchmarkLogger.setLevel(java.util.logging.Level.INFO);

        // 4. Setup File/Console Handler
        tempFile = Files.createTempFile("jul-bench", ".log");
        fileHandler = new FileHandler(tempFile.toString());
        fileHandler.setFormatter(new SimpleFormatter());
        fileHandler.setLevel(java.util.logging.Level.INFO);
        root.addHandler(fileHandler);

        // 5. Initialize the Frontends
        // Important: SLF4J, JCL, and Log4j must be fetched AFTER JUL is ready
        this.julLogger = benchmarkLogger;
        this.slf4jLogger = org.slf4j.LoggerFactory.getLogger(JulBenchmark.class);
        this.jclLogger = org.apache.commons.logging.LogFactory.getLog(JulBenchmark.class);
        this.log4jLogger = org.apache.logging.log4j.LogManager.getLogger(JulBenchmark.class);

        // 6. Force-check Log4j (Sometimes bridge needs a nudge)
        if (!log4jLogger.isInfoEnabled()) {
            System.out.println("LOG4J STILL DISABLED - FORCING...");
            // This is a last-resort hack for some log4j-to-jul versions
            org.apache.logging.log4j.core.config.Configurator.setLevel(
                    JulBenchmark.class.getName(),
                    org.apache.logging.log4j.Level.INFO
            );
        }

        System.out.println("JCL INFO: " + jclLogger.isInfoEnabled());
        System.out.println("Log4j INFO: " + log4jLogger.isInfoEnabled());
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
}
