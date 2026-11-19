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

import io.jstach.rainbowgum.RainbowGum;
import org.openjdk.jmh.annotations.Param;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RbGumParallelBenchmark extends ParallelLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    private RainbowGum rainbowGum;
    private Path rainbowTempFile;

    @Override
    public String backend() {
        return "rainbowgum";
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    void setupLogging(String format) throws IOException {
        rainbowTempFile = Files.createTempFile("rainbowgum-parallel-bench", ".log");
        rainbowGum = RbGumBenchmark.configureRainbowGum(category, "DEFAULT", rainbowTempFile);
        super.setupLogging(format);
    }

    @Override
    public void setupBackend() {
        // Rainbow Gum must be installed before the shared setup obtains SLF4J loggers.
    }

    @Override
    protected Path outputFile() {
        return rainbowTempFile;
    }

    @Override
    void tearDownLogging() {
        if (rainbowGum != null) {
            rainbowGum.close();
        }
        super.tearDownLogging();
        try {
            Files.deleteIfExists(rainbowTempFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}