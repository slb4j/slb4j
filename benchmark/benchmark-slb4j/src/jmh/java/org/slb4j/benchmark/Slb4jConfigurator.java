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

import org.slb4j.LogLayout;
import org.slb4j.SLB4J;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.FileHandler;
import org.slb4j.layout.PatternLayout;

import java.io.IOException;
import java.nio.file.Path;

public final class Slb4jConfigurator implements BenchmarkConfigurator {

    private final String category;
    private final String format;
    private final Path outputFile;
    private FileHandler fileHandler;

    public Slb4jConfigurator(String category, String format, Path outputFile) {
        this.category = category;
        this.format = format;
        this.outputFile = outputFile;
    }

    @Override
    public void setup() throws IOException {
        SLB4J.init();

        LogLayout layout = PatternLayout.parseLog4jPattern(AbstractLoggingBenchmark.getPattern(format));
        UniversalDispatcher dispatcher = UniversalDispatcher.getInstance();
        dispatcher.getLogHandlers().forEach(dispatcher::removeLogHandler);

        if ("CONSOLE".equals(category)) {
            ConsoleHandler consoleHandler = new ConsoleHandler("console", System.out, !"DETAILED".equals(format));
            consoleHandler.setLayout(layout);
            dispatcher.addLogHandler(consoleHandler);
        } else {
            fileHandler = new FileHandler("file", outputFile.toString(), false);
            fileHandler.setLayout(layout);
            dispatcher.addLogHandler(fileHandler);
        }
    }

    @Override
    public void tearDown() {
        if (fileHandler != null) {
            fileHandler.close();
        }
    }
}