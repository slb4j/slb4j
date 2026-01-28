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
package org.slb4j;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplesTest {

    @Test
    void testSampleAll() throws Exception {
        runSample("all", List.of(
                "Message from JUL",
                "Message from JCL",
                "Message from Log4j",
                "Message from SLF4J"
        ));
    }

    @Test
    void testSampleJul() throws Exception {
        runSample("jul", List.of("Hello from JUL!"));
    }

    @Test
    void testSampleJcl() throws Exception {
        runSample("jcl", List.of("Hello from JCL!"));
    }

    @Test
    void testSampleLog4j() throws Exception {
        runSample("log4j", List.of("Hello from Log4j!"));
    }

    @Test
    void testSampleSlf4j() throws Exception {
        runSample("slf4j", List.of("Hello from SLF4J!"));
    }

    private static void runSample(String sampleName, List<String> expectedOutputs) throws IOException, InterruptedException {
        Path projectRoot = Objects.requireNonNull(Paths.get(System.getProperty("user.dir")));

        String gradlew = projectRoot.resolve(System.getProperty("os.name").toLowerCase().contains("win") ? "gradlew.bat" : "gradlew").toString();

        List<String> command = new ArrayList<>();
        command.add(gradlew);
        command.add("--quiet");
        command.add(":samples:" + sampleName + ":run");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[" + sampleName + "] " + line);
                output.add(line);
            }
        }

        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "Sample " + sampleName + " exited with code " + exitCode);

        for (String expected : expectedOutputs) {
            assertTrue(output.stream().anyMatch(l -> l.contains(expected)),
                    "Output of sample " + sampleName + " did not contain: " + expected);
        }
    }
}
