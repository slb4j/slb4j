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

    private void runSample(String sampleName, List<String> expectedOutputs) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

        Path projectRoot = Objects.requireNonNull(Paths.get(System.getProperty("user.dir")));
        if (projectRoot.endsWith("slb4j")) {
            projectRoot = Objects.requireNonNull(projectRoot.getParent());
        }

        Path slb4jClasses = projectRoot.resolve("slb4j/build/classes/java/main");
        Path sampleClasses = projectRoot.resolve("slb4j/samples/" + sampleName + "/build/classes/java/main");
        Path sampleResources = projectRoot.resolve("slb4j/samples/" + sampleName + "/build/resources/main");

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
                    Path execFile = projectRoot.resolve("slb4j/build/jacoco/samples-" + sampleName + ".exec");
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
