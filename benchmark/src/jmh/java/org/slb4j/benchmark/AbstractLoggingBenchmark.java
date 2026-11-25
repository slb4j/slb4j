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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public abstract class AbstractLoggingBenchmark {

    private static final String SLF4J = "slf4j";
    private static final String LOG4J = "log4j";
    private static final String JUL = "jul";
    private static final String JCL = "jcl";

    private static final String CONSTANT = "CONSTANT";
    private static final String ARGUMENTS = "ARGUMENTS";

    private static final String LOG_MESSAGE_FORMAT_STRING = "Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s";
    private static final String PARAMETERIZED_LOG_MESSAGE = "Benchmark backend={} frontend={} category={} format={} messageType={}";
    private static final String JUL_LOG_MESSAGE = "Benchmark backend={0} frontend={1} category={2} format={3} messageType={4}";

    protected static String getPattern(String format) {
        return switch (format) {
            case "COMPACT" -> "%d{HH:mm:ss.SSS} %-5p %-30.30c - %m%n%ex";
            case "DEFAULT" -> "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %c - %msg%n%ex";
            case "DETAILED" -> "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5p %X{userId} (%C.%M(%F:%L)) - %m%n%ex";
            default -> throw new IllegalArgumentException("Invalid format: " + format);
        };
    }

    @Param({"false", "true"})
    public String outputToFile;

    @Param({CONSTANT, ARGUMENTS})
    public String messageType;

    public String category; // Will be set in subclasses
    public String format;   // Will be set in subclasses

    protected String logMessage;

    protected PrintStream originalOut;
    protected PrintStream originalErr;
    protected FileOutputStream fileOut;
    protected Path tempFile;

    private SwitchableOutputStream stdoutOutput;
    private SwitchableOutputStream stderrOutput;

    protected org.slf4j.Logger slf4jLogger;
    protected org.apache.logging.log4j.Logger log4jLogger;
    protected java.util.logging.Logger julLogger;
    protected org.apache.commons.logging.Log jclLogger;

    protected org.slf4j.Marker slf4jMarker;
    protected org.apache.logging.log4j.Marker log4jMarker;

    private BenchmarkConfigurator configurator;

    public abstract String backend();

    @Setup(Level.Trial)
    @SuppressWarnings("java:S106")
    public void setup(org.openjdk.jmh.infra.BenchmarkParams params) throws IOException {
        validateMessageType(messageType);

        originalOut = System.out;
        originalErr = System.err;

        // Print testing info only once per fork/trial
        String benchmarkName = params.getBenchmark();
        String frontend = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);
        originalOut.println("Testing " + backend() + "-" + frontend + " ...");

        stdoutOutput = new SwitchableOutputStream(originalOut);
        stderrOutput = new SwitchableOutputStream(originalErr);
        System.setOut(new PrintStream(stdoutOutput, false, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderrOutput, false, StandardCharsets.UTF_8));

        String benchmarkCategory = params.getParam("category");
        String benchmarkFormat = params.getParam("format");
        if ("true".equals(outputToFile)) {
            fileOut = new FileOutputStream("benchmark.out", true);
        }
        tempFile = Files.createTempFile(backend() + "-bench", ".log");
        configurator = createConfigurator(benchmarkCategory, benchmarkFormat, tempFile);
        configurator.setup();
        initializeLoggers(benchmarkFormat);

        OutputStream benchmarkOutput = fileOut != null ? fileOut : OutputStream.nullOutputStream();
        stdoutOutput.setTarget(benchmarkOutput);
        stderrOutput.setTarget(benchmarkOutput);
    }

    final void initializeLoggers(String benchmarkFormat) {
        Class<? extends AbstractLoggingBenchmark> benchmarkClass = getClass();
        slf4jLogger = org.slf4j.LoggerFactory.getLogger(benchmarkClass);
        log4jLogger = org.apache.logging.log4j.LogManager.getLogger(benchmarkClass);
        julLogger = java.util.logging.Logger.getLogger(benchmarkClass.getName());
        jclLogger = org.apache.commons.logging.LogFactory.getLog(benchmarkClass);
        slf4jMarker = org.slf4j.MarkerFactory.getMarker("BENCH");
        log4jMarker = org.apache.logging.log4j.MarkerManager.getMarker("BENCH");

        if ("DETAILED".equals(benchmarkFormat)) {
            org.slf4j.MDC.put("userId", "benchUser");
            org.apache.logging.log4j.ThreadContext.put("userId", "benchUser");
        }
    }

    protected void updateLogMessage(org.openjdk.jmh.infra.BenchmarkParams params, String category, String format) {
        String benchmarkName = params.getBenchmark();
        String frontend = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);
        logMessage = LOG_MESSAGE_FORMAT_STRING.formatted(backend(), frontend, category, format, messageType);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        System.setOut(originalOut);
        System.setErr(originalErr);
        if (fileOut != null) {
            fileOut.close();
        }
        clearLoggingContext();
        if (configurator != null) {
            configurator.tearDown();
        }
        Files.deleteIfExists(tempFile);
    }

    static void clearLoggingContext() {
        org.slf4j.MDC.clear();
        org.apache.logging.log4j.ThreadContext.clearAll();
    }

    protected abstract BenchmarkConfigurator createConfigurator(String category, String format, Path tempFile);

    private static void validateMessageType(String type) {
        if (!CONSTANT.equals(type) && !ARGUMENTS.equals(type)) {
            throw new IllegalArgumentException("Unsupported messageType " + type);
        }
    }

    @Benchmark
    public void slf4j() {
        if ("MARKER".equals(format)) {
            switch (messageType) {
                case CONSTANT -> slf4jLogger.info(slf4jMarker, logMessage);
                case ARGUMENTS ->
                        slf4jLogger.info(slf4jMarker, PARAMETERIZED_LOG_MESSAGE, backend(), SLF4J, category, format, messageType);
                default -> {/* do nothing */}
            }
        } else {
            switch (messageType) {
                case CONSTANT -> slf4jLogger.info(logMessage);
                case ARGUMENTS ->
                        slf4jLogger.info(PARAMETERIZED_LOG_MESSAGE, backend(), SLF4J, category, format, messageType);
                default -> {/* do nothing */}
            }
        }
    }

    @Benchmark
    public void log4j() {
        if ("MARKER".equals(format)) {
            switch (messageType) {
                case CONSTANT -> log4jLogger.info(log4jMarker, logMessage);
                case ARGUMENTS ->
                        log4jLogger.info(log4jMarker, PARAMETERIZED_LOG_MESSAGE, backend(), LOG4J, category, format, messageType);
                default -> {/* do nothing */}
            }
        } else {
            switch (messageType) {
                case CONSTANT -> log4jLogger.info(logMessage);
                case ARGUMENTS ->
                        log4jLogger.info(PARAMETERIZED_LOG_MESSAGE, backend(), LOG4J, category, format, messageType);
                default -> {/* do nothing */}
            }
        }
    }

    @Benchmark
    public void jul() {
        switch (messageType) {
            case CONSTANT -> julLogger.info(logMessage);
            case ARGUMENTS ->
                    julLogger.log(java.util.logging.Level.INFO, JUL_LOG_MESSAGE, new Object[]{backend(), JUL, category, format, messageType});
            default -> {assert false : "unsupported message type for JUL: " + messageType;}
        }
    }

    @Benchmark
    public void jcl() {
        assert CONSTANT.equals(messageType) : "unsupported message type for JCL: " + messageType;
        jclLogger.info(logMessage);
    }
}
