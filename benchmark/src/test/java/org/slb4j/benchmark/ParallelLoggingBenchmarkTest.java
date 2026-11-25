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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openjdk.jmh.annotations.Param;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParallelLoggingBenchmarkTest {
    @Test
    void jmhDefaultsToOnlyConstantMessages() throws NoSuchFieldException {
        var param = TestBenchmark.class.getField("messageType").getAnnotation(Param.class);
        assertArrayEquals(new String[]{"CONSTANT"}, param.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONSTANT", "ARGUMENTS"})
    void julWorkloadsUseTheExpectedApiAndRenderTheSameMessage(String messageType) {
        var benchmark = new TestBenchmark();
        var logger = new RecordingJulLogger();
        benchmark.julLogger = logger;
        benchmark.messageType = messageType;
        benchmark.configureWorkload("jul");

        benchmark.jul_1();

        assertEquals(1, logger.records.size());
        LogRecord record = logger.records.getFirst();
        assertNull(record.getThrown());
        if ("ARGUMENTS".equals(messageType)) {
            assertArrayEquals(new Object[]{"test"}, record.getParameters());
        } else {
            assertNull(record.getParameters());
        }
        assertEquals("Parallel benchmark jul backend=test", new SimpleFormatter().formatMessage(record));
    }

    @ParameterizedTest
    @CsvSource({"jcl, ARGUMENTS", "jul, MESSAGE_SUPPLIER", "slf4j, LAMBDA_PARAMETER", "slf4j, UNKNOWN"})
    void rejectsUnsupportedPairsBeforeMeasurement(String frontend, String messageType) {
        var benchmark = new TestBenchmark();
        benchmark.messageType = messageType;

        var exception = assertThrows(IllegalArgumentException.class, () -> benchmark.configureWorkload(frontend));

        assertEquals("Unsupported messageType " + messageType + " for parallel frontend " + frontend, exception.getMessage());
    }

    private static final class TestBenchmark extends ParallelLoggingBenchmark {
        @Override
        public String backend() {
            return "test";
        }

        @Override
        public String category() {
            return "CONSOLE";
        }

        @Override
        protected BenchmarkConfigurator createConfigurator(String category, String format, Path tempFile) {
            throw new AssertionError("Backend configuration is not needed to test workload dispatch");
        }
    }

    private static final class RecordingJulLogger extends Logger {
        private final List<LogRecord> records = new ArrayList<>();

        RecordingJulLogger() {
            super("test", null);
        }

        @Override
        public void log(LogRecord record) {
            records.add(record);
        }
    }
}