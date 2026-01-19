/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slb4j.benchmark;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public abstract class ParallelLoggingBenchmark {

    protected org.slf4j.Logger slf4jLogger;
    protected org.apache.logging.log4j.Logger log4jLogger;

    private PrintStream originalOut;
    private PrintStream originalErr;

    public abstract String backend();
    public abstract String category();

    @Setup(Level.Trial)
    public void setup(org.openjdk.jmh.infra.BenchmarkParams params) throws IOException {
        originalOut = System.out;
        originalErr = System.err;

        // Print testing info only once per fork/trial
        String benchmarkName = params.getBenchmark();
        String frontend = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);
        originalOut.println("Testing parallel " + backend() + "-" + frontend + " ...");

        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));

        setupLogging();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        System.setOut(originalOut);
        System.setErr(originalErr);
        tearDownLogging();
    }

    protected abstract void setupLogging() throws IOException;

    protected abstract void tearDownLogging();

    @Benchmark
    @Threads(1)
    public void slf4j_1() {
        slf4j();
    }

    @Benchmark
    @Threads(2)
    public void slf4j_2() {
        slf4j();
    }

    @Benchmark
    @Threads(4)
    public void slf4j_4() {
        slf4j();
    }

    @Benchmark
    @Threads(8)
    public void slf4j_8() {
        slf4j();
    }

    @Benchmark
    @Threads(16)
    public void slf4j_16() {
        slf4j();
    }

    @Benchmark
    @Threads(64)
    public void slf4j_64() {
        slf4j();
    }

    @Benchmark
    @Threads(128)
    public void slf4j_128() {
        slf4j();
    }

    @Benchmark
    @Threads(1)
    public void log4j_1() {
        log4j();
    }

    @Benchmark
    @Threads(2)
    public void log4j_2() {
        log4j();
    }

    @Benchmark
    @Threads(4)
    public void log4j_4() {
        log4j();
    }

    @Benchmark
    @Threads(8)
    public void log4j_8() {
        log4j();
    }

    @Benchmark
    @Threads(16)
    public void log4j_16() {
        log4j();
    }

    @Benchmark
    @Threads(64)
    public void log4j_64() {
        log4j();
    }

    @Benchmark
    @Threads(128)
    public void log4j_128() {
        log4j();
    }

    private void slf4j() {
        if (slf4jLogger.isInfoEnabled()) {
            slf4jLogger.info("Parallel benchmark slf4j backend={}", backend());
        }
    }

    private void log4j() {
        log4jLogger.info(() -> "Parallel benchmark log4j backend=" + backend());
    }
}
