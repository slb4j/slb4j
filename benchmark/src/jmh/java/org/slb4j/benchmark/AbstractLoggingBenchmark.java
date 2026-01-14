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

    protected static final String MESSAGE = "Benchmark log message";
    
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
            fileOut = new FileOutputStream("benchmark.out");
            PrintStream ps = new PrintStream(fileOut);
            System.setOut(ps);
            System.setErr(ps);
        } else {
            System.setOut(new PrintStream(new NullOutputStream()));
            System.setErr(new PrintStream(new NullOutputStream()));
        }
        setupLogging();
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
