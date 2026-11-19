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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleOutputTest {

    @Test
    void groupsBackendsAndPreservesExceptionsWithoutDuplicates() throws Exception {
        Path report = Files.createTempFile(Path.of("build"), "sample-output-test-", ".md");
        try {
            Files.writeString(report, section("COMPACT", "JUL", false) + section("COMPACT", "JUL", true)
                    + section("DEFAULT", "JUL", false));
            List<SampleOutput.Sample> samples = List.of(
                    new SampleOutput.Sample("JUL", false, "one line\n"),
                    new SampleOutput.Sample("JUL", true, "message\njava.lang.IllegalStateException\n\tat example.run\n"));
            try (var executor = Executors.newFixedThreadPool(3)) {
                var jul = executor.submit(() -> { SampleOutput.appendSamples(report, "COMPACT", "JUL", samples); return null; });
                var log4j = executor.submit(() -> { SampleOutput.appendSamples(report, "COMPACT", "LOG4J", samples); return null; });
                var duplicate = executor.submit(() -> { SampleOutput.appendSamples(report, "COMPACT", "JUL", samples); return null; });
                jul.get();
                log4j.get();
                duplicate.get();
            }
            String text = Files.readString(report);
            assertEquals(1, count(text, "JUL:        one line"));
            assertEquals(1, count(text, "LOG4J:      one line"));
            assertEquals(1, count(text, "<!-- sample-output:COMPACT:JUL:exception:JUL -->"));
            assertTrue(text.contains("JUL:        message\n            java.lang.IllegalStateException\n            \tat example.run"));
            assertTrue(text.endsWith(section("DEFAULT", "JUL", false)));
        } finally {
            Files.deleteIfExists(report);
        }
    }

    @Test
    void rejectsMissingOrEmptySamplesWithoutPartiallyWriting() throws Exception {
        Path report = Files.createTempFile(Path.of("build"), "sample-output-test-", ".md");
        try {
            String original = section("COMPACT", "JUL", false);
            Files.writeString(report, original);
            assertThrows(IOException.class, () -> SampleOutput.appendSamples(report, "COMPACT", "JUL", List.of(
                    new SampleOutput.Sample("JUL", false, "valid"), new SampleOutput.Sample("JUL", true, "  "))));
            assertEquals(original, Files.readString(report));
            assertThrows(IOException.class, () -> SampleOutput.appendSamples(report, "DETAILED", "JUL", List.of(
                    new SampleOutput.Sample("JUL", false, "valid"))));
            assertEquals(original, Files.readString(report));
        } finally {
            Files.deleteIfExists(report);
        }
    }

    @Test
    void rejectsTruncatedMessagesOrMissingExceptions() throws Exception {
        assertThrows(IOException.class, () -> SampleOutput.validateCaptured("JUL", "LOGBACK", false, "message", "layout prefix"));
        assertThrows(IOException.class, () -> SampleOutput.validateCaptured("JUL", "LOGBACK", true, "message", "layout message"));
        SampleOutput.validateCaptured("JUL", "LOGBACK", false, "message", "layout message");
        SampleOutput.validateCaptured("JUL", "LOGBACK", true, "message", "layout message\njava.lang.IllegalStateException: Sample exception");
    }

    private static String section(String format, String frontend, boolean exception) {
        return "## Format: " + format + ", Frontend " + frontend + (exception ? " (with exception)" : "")
                + "\n\n````text\n````\n\n";
    }

    private static int count(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}