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
import org.slb4j.LogLayout;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.FileHandler;
import org.slb4j.layout.PatternLayout;

import java.io.IOException;

import static org.slb4j.layout.PatternLayout.DEFAULT_PATTERN_STRING;

public class Slb4jParallelBenchmark extends ParallelLoggingBenchmark {

    @Param({"CONSOLE", "FILE"})
    public String category;

    @Override
    public String backend() {
        return "slb4j";
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public void setupBackend() throws IOException {
        UniversalDispatcher dispatcher = UniversalDispatcher.getInstance();
        dispatcher.getLogHandlers().forEach(dispatcher::removeLogHandler);

        if ("CONSOLE".equals(category)) {
            LogLayout pattern = PatternLayout.LAYOUT_INSTANCE_DEFAULT;
            ConsoleHandler consoleHandler = new ConsoleHandler("console", System.out, true);
            consoleHandler.setLayout(pattern);
            dispatcher.addLogHandler(consoleHandler);
        } else {
            // use pattern without highlighting
            LogLayout pattern = PatternLayout.parseLog4jPattern(DEFAULT_PATTERN_STRING);
            FileHandler fileHandler = new FileHandler("file", tempFile.toString(), false);
            fileHandler.setLayout(pattern);
            dispatcher.addLogHandler(fileHandler);
        }
    }
}
