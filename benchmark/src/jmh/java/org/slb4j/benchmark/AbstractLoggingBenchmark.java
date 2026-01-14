package org.slb4j.benchmark;

import org.openjdk.jmh.annotations.*;
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

    public String category; // Will be set in subclasses
    public String format;   // Will be set in subclasses

    protected String logMessage;
    
    protected static class NullOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {}
        @Override
        public void write(byte[] b) throws IOException {}
        @Override
        public void write(byte[] b, int off, int len) throws IOException {}
    }

    protected PrintStream originalOut;
    protected PrintStream originalErr;
    protected FileOutputStream fileOut;

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
            System.setOut(new PrintStream(new NullOutputStream()));
            System.setErr(new PrintStream(new NullOutputStream()));
        }
        setupLogging();
    }

    protected void updateLogMessage(org.openjdk.jmh.infra.BenchmarkParams params, String category, String format) {
        String benchmarkName = params.getBenchmark();
        String frontend = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);
        logMessage = String.format("Benchmark backend=%s frontend=%s category=%s format=%s", 
            backend, frontend, category, format);
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

    // Benchmark methods will be implemented in subclasses
}
