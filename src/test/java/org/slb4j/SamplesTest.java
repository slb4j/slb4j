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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    private void runSample(String sampleName, List<String> expectedOutputs) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

        Path projectRoot = Paths.get(System.getProperty("user.dir"));

        String slb4jClassesProp = System.getProperty("slb4j.main.classes");
        Path slb4jClasses = slb4jClassesProp != null ? Paths.get(slb4jClassesProp) : projectRoot.resolve("build/classes/java/main");

        String sampleClassesProp = System.getProperty("slb4j.sample.classes." + sampleName);
        Path sampleClasses = sampleClassesProp != null ? Paths.get(sampleClassesProp) : projectRoot.resolve("samples/" + sampleName + "/build/classes/java/main");

        String sampleResourcesProp = System.getProperty("slb4j.sample.resources." + sampleName);
        Path sampleResources = sampleResourcesProp != null ? Paths.get(sampleResourcesProp) : projectRoot.resolve("samples/" + sampleName + "/build/resources/main");

        String classpath = System.getProperty("java.class.path");
        String combinedClasspath = String.join(File.pathSeparator,
                sampleClasses.toString(),
                sampleResources.toString(),
                slb4jClasses.toString(),
                classpath
        );

        String mainClass = "org.slb4j.samples." + sampleName + ".Main";

        List<String> command = new ArrayList<>();
        command.add(javaBin);

        // Pass JaCoCo agent if present
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();
        for (String arg : arguments) {
            if (arg.startsWith("-javaagent:") && arg.contains("jacoco")) {
                String jacocoArg = arg;
                if (jacocoArg.contains("destfile=")) {
                    Path execFile = projectRoot.resolve("build/jacoco/samples-" + sampleName + ".exec");
                    jacocoArg = jacocoArg.replaceAll("destfile=[^,]+", "destfile=" + execFile);
                }
                // Exclude Log4j from instrumentation to avoid initialization issues
                jacocoArg += ",excludes=org.apache.logging.log4j.*";
                command.add(jacocoArg);
            }
        }

        command.addAll(List.of(
                "-Dlog4j2.loggerContextFactory=slb4j.frontend.log4j.Log4jLoggerContextFactory",
                "-Dslf4j.provider=slb4j.frontend.slf4j.LoggingServiceProviderSlf4j",
                "-Dorg.apache.commons.logging.LogFactory=org.apache.commons.logging.impl.LogFactoryImpl",
                "-Dorg.apache.commons.logging.Log=slb4j.frontend.jcl.LoggerJcl",
                "-cp", combinedClasspath,
                mainClass
        ));

        ProcessBuilder pb = new ProcessBuilder(command);
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
