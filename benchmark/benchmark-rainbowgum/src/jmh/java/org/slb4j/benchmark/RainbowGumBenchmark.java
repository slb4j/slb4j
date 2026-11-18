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

import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.file.FileOutputBuilder;
import io.jstach.rainbowgum.pattern.format.PatternEncoderBuilder;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RainbowGumBenchmark extends AbstractLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    @Param({"COMPACT", "DEFAULT", "DETAILED"})
    public String format;

    private RainbowGum rainbowGum;

    @Override
    public String backend() {
        return "rainbowgum";
    }

    @Override
    protected void setupLogging() throws IOException {
        tempFile = Files.createTempFile("rainbowgum-bench", ".log");
        rainbowGum = configureRainbowGum(category, format, tempFile);

        slf4jLogger = LoggerFactory.getLogger(RainbowGumBenchmark.class);
        log4jLogger = org.apache.logging.log4j.LogManager.getLogger(RainbowGumBenchmark.class);
        julLogger = java.util.logging.Logger.getLogger(RainbowGumBenchmark.class.getName());
        jclLogger = org.apache.commons.logging.LogFactory.getLog(RainbowGumBenchmark.class);

        if ("DETAILED".equals(format)) {
            org.slf4j.MDC.put("userId", "benchUser");
        }
    }

    @Override
    protected void tearDownLogging() {
        org.slf4j.MDC.clear();
        if (rainbowGum != null) {
            rainbowGum.close();
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Setup(Level.Iteration)
    public void setupIteration(org.openjdk.jmh.infra.BenchmarkParams params) {
        updateLogMessage(params, category, format);
    }

    static RainbowGum configureRainbowGum(String category, String format, Path tempFile) {
        String pattern = switch (format) {
            case "COMPACT" -> "%d{HH:mm:ss.SSS} %-5level %-30.30logger{0} - %msg%n";
            case "DETAILED" ->
                    "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %X{userId} \\(%class.%method\\(%file:%line\\)\\) - %msg%n";
            case "DEFAULT" -> "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n";
            default -> throw new IllegalArgumentException("Invalid format: " + format);
        };

        return RainbowGum.builder(config -> {
            if ("DETAILED".equals(format)) {
                config.properties(LogProperties.builder()
                        .fromProperties("logging.caller.logger.org.slb4j.benchmark=true")
                        .build());
            }
        }).route(route -> {
            route.level(System.Logger.Level.INFO);
            route.appender("benchmark", appender -> {
                if ("FILE".equals(category)) {
                    appender.output(new FileOutputBuilder("benchmark").fileName(tempFile.toString()).append(false).build());
                } else {
                    appender.output(LogOutput.ofStandardOut());
                }
                appender.encoder(new PatternEncoderBuilder("benchmark").pattern(pattern).build());
            });
        }).set();
    }
}