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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

public final class JulConfigurator implements BenchmarkConfigurator {

    private final String category;
    private final String format;
    private final Path outputFile;
    private final Class<?> benchmarkClass;
    private Handler handler;

    public JulConfigurator(String category, String format, Path outputFile, Class<?> benchmarkClass) {
        this.category = category;
        this.format = format;
        this.outputFile = outputFile;
        this.benchmarkClass = benchmarkClass;
    }

    @Override
    public void setup() throws IOException {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Jdk14Logger");
        org.apache.commons.logging.LogFactory.releaseAll();

        Logger root = Logger.getLogger("");
        for (Handler rootHandler : root.getHandlers()) {
            root.removeHandler(rootHandler);
        }
        root.setLevel(java.util.logging.Level.INFO);
        Logger.getLogger(benchmarkClass.getName()).setLevel(java.util.logging.Level.INFO);

        if ("CONSOLE".equals(category)) {
            handler = new java.util.logging.ConsoleHandler();
        } else {
            Files.deleteIfExists(outputFile);
            handler = new FileHandler(outputFile.toString());
        }
        handler.setFormatter(JulBenchmarkFormatting.newFormatter(format));
        handler.setLevel(java.util.logging.Level.INFO);
        root.addHandler(handler);
    }

    @Override
    public void tearDown() {
        if (handler != null) {
            handler.close();
        }
    }
}