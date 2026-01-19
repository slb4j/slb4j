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

import org.openjdk.jmh.annotations.Param;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class JulParallelBenchmark extends ParallelLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    private Path tempFile;
    private FileHandler fileHandler;

    @Override
    public String backend() {
        return "jul";
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    protected void setupLogging() throws IOException {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Jdk14Logger");
        org.apache.commons.logging.LogFactory.releaseAll();

        LogManager manager = LogManager.getLogManager();
        Logger root = Logger.getLogger("");
        for (java.util.logging.Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }

        root.setLevel(java.util.logging.Level.INFO);

        Logger benchmarkLogger = Logger.getLogger(JulParallelBenchmark.class.getName());
        benchmarkLogger.setLevel(java.util.logging.Level.INFO);

        tempFile = Files.createTempFile("jul-parallel-bench", ".log");
        fileHandler = new FileHandler(tempFile.toString());
        fileHandler.setFormatter(new SimpleFormatter());
        fileHandler.setLevel(java.util.logging.Level.INFO);
        root.addHandler(fileHandler);

        this.slf4jLogger = org.slf4j.LoggerFactory.getLogger(JulParallelBenchmark.class);
        this.log4jLogger = org.apache.logging.log4j.LogManager.getLogger(JulParallelBenchmark.class);
    }

    @Override
    protected void tearDownLogging() {
        if (fileHandler != null) {
            fileHandler.close();
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
