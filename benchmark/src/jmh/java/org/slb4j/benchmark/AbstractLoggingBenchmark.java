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
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
    private static final String MESSAGE_SUPPLIER = "MESSAGE_SUPPLIER";
    private static final String LAMBDA_PARAMETER = "LAMBDA_PARAMETER";

    private static final String LOG_MESSAGE_FORMAT_STRING = "Benchmark backend=%s frontend=%s category=%s format=%s messageType=%s";
    private static final String PARAMETERIZED_LOG_MESSAGE = "Benchmark backend={} frontend={} category={} format={} messageType={}";
    private static final String JUL_LOG_MESSAGE = "Benchmark backend={0} frontend={1} category={2} format={3} messageType={4}";

    protected static String getPattern(String format) {
        return switch (format) {
            case "COMPACT" -> "%d{HH:mm:ss.SSS} %-5p %-30.30c - %m%ex%n";
            case "DETAILED" -> "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5p %X{userId} (%C.%M(%F:%L)) - %m%ex%n";
            case "DEFAULT" -> "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %c{1.} - %msg%ex%n";
            default -> throw new IllegalArgumentException("Invalid format: " + format);
        };
    }

    @Param({"false", "true"})
    public String outputToFile;

    @Param({CONSTANT, ARGUMENTS, MESSAGE_SUPPLIER, LAMBDA_PARAMETER})
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
            String sampleDirectory = params.getParam("sampleDirectory");
            if (sampleDirectory == null) {
                fileOut = new FileOutputStream("benchmark.out", true);
            } else if (!sampleDirectory.isEmpty()) {
                String exceptionSuffix = Boolean.parseBoolean(params.getParam("sampleException")) ? "-EXCEPTION" : "";
                String filename = "%s-%s-%s%s.out".formatted(benchmarkFormat,
                        params.getParam("sampleFrontend"), params.getParam("sampleMessageType"), exceptionSuffix);
                fileOut = new FileOutputStream(Path.of(sampleDirectory, filename).toFile(), true);
            }
        }
        tempFile = Files.createTempFile(backend() + "-bench", ".log");
        configurator = createConfigurator(benchmarkCategory, benchmarkFormat, tempFile);
        configurator.setup();

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

        OutputStream benchmarkOutput = fileOut != null ? fileOut : OutputStream.nullOutputStream();
        stdoutOutput.setTarget(benchmarkOutput);
        stderrOutput.setTarget(benchmarkOutput);
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
        org.slf4j.MDC.clear();
        org.apache.logging.log4j.ThreadContext.clearAll();
        if (configurator != null) {
            configurator.tearDown();
        }
        Files.deleteIfExists(tempFile);
    }

    protected abstract BenchmarkConfigurator createConfigurator(String category, String format, Path tempFile);

    @State(Scope.Benchmark)
    public static class SampleParameters {
        @Param({"jul"})
        public String sampleFrontend;

        @Param({CONSTANT, ARGUMENTS, MESSAGE_SUPPLIER, LAMBDA_PARAMETER})
        public String sampleMessageType;

        @Param({"false", "true"})
        public boolean sampleException;

        @Param({""})
        public String sampleDirectory;
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public void sampleOutput(SampleParameters sample) {
        String frontend = sample.sampleFrontend.toUpperCase(Locale.ROOT);
        String message = "Sample log output generated by " + frontend + " frontend/" + backend() + " backend";
        Throwable exception = sample.sampleException ? new IllegalStateException("Sample exception") : null;
        switch (sample.sampleMessageType) {
            case CONSTANT -> {
                switch (sample.sampleFrontend) {
                    case JUL -> julLogger.log(java.util.logging.Level.INFO, message, exception);
                    case JCL -> jclLogger.info(message, exception);
                    case LOG4J -> log4jLogger.info(message, exception);
                    case SLF4J -> slf4jLogger.info(message, exception);
                    default -> {/* ignore */}
                }
            }
            case ARGUMENTS -> {
                switch (sample.sampleFrontend) {
                    case JUL -> {
                        java.util.logging.LogRecord record = new java.util.logging.LogRecord(java.util.logging.Level.INFO,
                                "Sample log output generated by {0} frontend/{1} backend");
                        record.setLoggerName(julLogger.getName());
                        record.setParameters(new Object[]{frontend, backend()});
                        record.setThrown(exception);
                        julLogger.log(record);
                    }
                    case LOG4J -> log4jLogger.info("Sample log output generated by {} frontend/{} backend", frontend, backend(), exception);
                    case SLF4J -> slf4jLogger.info("Sample log output generated by {} frontend/{} backend", frontend, backend(), exception);
                    default -> {/* ignore */}
                }
            }
            case MESSAGE_SUPPLIER -> {
                Supplier<String> messageSupplier = () -> message;
                switch (sample.sampleFrontend) {
                    case JUL -> julLogger.log(java.util.logging.Level.INFO, exception, messageSupplier);
                    case LOG4J -> log4jLogger.info(messageSupplier::get, exception);
                    case SLF4J -> slf4jLogger.atInfo().setCause(exception).log(messageSupplier);
                    default -> {/* ignore */}
                }
            }
            case LAMBDA_PARAMETER -> {
                switch (sample.sampleFrontend) {
                    case LOG4J -> log4jLogger.atInfo().withThrowable(exception)
                            .log("Sample log output generated by {} frontend/{} backend", () -> frontend, this::backend);
                    case SLF4J -> slf4jLogger.atInfo().setCause(exception).addArgument(() -> frontend)
                            .addArgument(this::backend).log("Sample log output generated by {} frontend/{} backend");
                    default -> {/* ignore */}
                }
            }
        }
    }

    @Benchmark
    public void slf4j() {
        if ("MARKER".equals(format)) {
            switch (messageType) {
                case CONSTANT -> slf4jLogger.info(slf4jMarker, logMessage);
                case ARGUMENTS ->
                        slf4jLogger.info(slf4jMarker, PARAMETERIZED_LOG_MESSAGE, backend(), SLF4J, category, format, messageType);
                case MESSAGE_SUPPLIER ->
                        slf4jLogger.atInfo().addMarker(slf4jMarker).log(() -> logMessage);
                case LAMBDA_PARAMETER ->
                        slf4jLogger.atInfo().addMarker(slf4jMarker).addArgument(this::backend).addArgument(() -> SLF4J).addArgument(() -> category).addArgument(() -> format).addArgument(() -> messageType).log(PARAMETERIZED_LOG_MESSAGE);
                default -> {/* do nothing */}
            }
        } else {
            switch (messageType) {
                case CONSTANT -> slf4jLogger.info(logMessage);
                case ARGUMENTS ->
                        slf4jLogger.info(PARAMETERIZED_LOG_MESSAGE, backend(), SLF4J, category, format, messageType);
                case MESSAGE_SUPPLIER ->
                        slf4jLogger.atInfo().log(() -> logMessage);
                case LAMBDA_PARAMETER ->
                        slf4jLogger.atInfo().addArgument(this::backend).addArgument(() -> SLF4J).addArgument(() -> category).addArgument(() -> format).addArgument(() -> messageType).log(PARAMETERIZED_LOG_MESSAGE);
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
                case MESSAGE_SUPPLIER -> log4jLogger.info(log4jMarker, () -> logMessage);
                case LAMBDA_PARAMETER ->
                        log4jLogger.info(log4jMarker, PARAMETERIZED_LOG_MESSAGE, this::backend, () -> LOG4J, () -> category, () -> format, () -> messageType);
                default -> {/* do nothing */}
            }
        } else {
            switch (messageType) {
                case CONSTANT -> log4jLogger.info(logMessage);
                case ARGUMENTS ->
                        log4jLogger.info(PARAMETERIZED_LOG_MESSAGE, backend(), LOG4J, category, format, messageType);
                case MESSAGE_SUPPLIER -> log4jLogger.info(() -> logMessage);
                case LAMBDA_PARAMETER ->
                        log4jLogger.info(PARAMETERIZED_LOG_MESSAGE, this::backend, () -> LOG4J, () -> category, () -> format, () -> messageType);
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
            case MESSAGE_SUPPLIER -> julLogger.info(() -> logMessage);
            default -> {assert false : "unsupported message type for JUL: " + messageType;}
        }
    }

    @Benchmark
    public void jcl() {
        assert CONSTANT.equals(messageType) : "unsupported message type for JCL: " + messageType;
        jclLogger.info(logMessage);
    }
}
