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

import java.nio.file.Path;

public final class RainbowGumConfigurator implements BenchmarkConfigurator {

    private final String category;
    private final String format;
    private final Path outputFile;
    private RainbowGum rainbowGum;

    public RainbowGumConfigurator(String category, String format, Path outputFile) {
        this.category = category;
        this.format = format;
        this.outputFile = outputFile;
    }

    @Override
    public void setup() {
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
        org.slf4j.bridge.SLF4JBridgeHandler.install();

        String pattern = switch (format) {
            case "COMPACT" -> "%d{HH:mm:ss.SSS} %-5level %-30.30logger{0} - %msg%n";
            case "DETAILED" ->
                    "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %X{userId} \\(%class.%method\\(%file:%line\\)\\) - %msg%n";
            case "DEFAULT" -> "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n";
            default -> throw new IllegalArgumentException("Invalid format: " + format);
        };

        rainbowGum = RainbowGum.builder(config -> {
            if ("DETAILED".equals(format)) {
                config.properties(LogProperties.builder()
                        .fromProperties("logging.caller.logger.org.slb4j.benchmark=true")
                        .build());
            }
        }).route(route -> {
            route.level(System.Logger.Level.INFO);
            route.appender("benchmark", appender -> {
                if ("FILE".equals(category)) {
                    appender.output(new FileOutputBuilder("benchmark").fileName(outputFile.toString()).append(false).build());
                } else {
                    appender.output(LogOutput.ofStandardOut());
                }
                appender.encoder(new PatternEncoderBuilder("benchmark").pattern(pattern).build());
            });
        }).set();
    }

    @Override
    public void tearDown() {
        org.slf4j.bridge.SLF4JBridgeHandler.uninstall();
        if (rainbowGum != null) {
            rainbowGum.close();
        }
    }
}