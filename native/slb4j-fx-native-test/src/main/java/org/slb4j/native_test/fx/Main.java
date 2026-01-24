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
package org.slb4j.native_test.fx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slb4j.LogFilter;
import org.slb4j.SLB4J;
import org.slb4j.ext.fx.FxLogPane;
import org.slb4j.filter.LoggerNameFilter;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Main extends Application {

    static {
        SLB4J.init();
        SLB4J.getDispatcher().setFilter(LogFilter.allPass());
        SLB4J.getDispatcher().setFilter(new LoggerNameFilter("root", logger -> logger.endsWith(".NativeTest")));
    }

    private static final int AVERAGE_SLEEP_MILLIS = 100;
    private static final int LOG_BUFFER_SIZE = 10_000;
    private static final org.slf4j.Logger SLF4J_LOGGER = LoggerFactory.getLogger("SLF4J.NativeTest");
    private static final Log JCL_LOGGER = LogFactory.getLog("JCL.NativeTest");
    private static final java.util.logging.Logger JUL_LOGGER = java.util.logging.Logger.getLogger("JUL.NativeTest");
    private static final org.apache.logging.log4j.Logger LOG4J_LOGGER = org.apache.logging.log4j.LogManager.getLogger("LOG4J.NativeTest");

    private final FxLogPane logPane;
    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger n = new AtomicInteger();

    public static void main(String[] args) {
        launch(args);
    }

    public Main() {
        logPane = new FxLogPane(LOG_BUFFER_SIZE);
    }

    @Override
    public void start(Stage primaryStage) {
        Scene scene = new Scene(logPane, 1024, 768);
        primaryStage.setTitle("SLB4J JavaFX Native Test");
        primaryStage.setScene(scene);
        primaryStage.show();

        startLoggingThreads();
    }

    private void startLoggingThreads() {
        final int numberOfImplementations = 4;
        for (int i = 0; i < numberOfImplementations; i++) {
            final int implementation = i;
            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(random.nextInt(2 * AVERAGE_SLEEP_MILLIS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    int nr = n.incrementAndGet();
                    int levelInt = random.nextInt(5);
                    String msg = "Message #" + nr + " from implementation " + implementation + " level " + levelInt;

                    switch (implementation) {
                        case 0 -> { // SLF4J
                            switch (levelInt) {
                                case 0 -> SLF4J_LOGGER.trace(msg);
                                case 1 -> SLF4J_LOGGER.debug(msg);
                                case 2 -> SLF4J_LOGGER.info(msg);
                                case 3 -> SLF4J_LOGGER.warn(msg);
                                case 4 -> SLF4J_LOGGER.error(msg);
                            }
                        }
                        case 1 -> { // JUL
                            switch (levelInt) {
                                case 0 -> JUL_LOGGER.finest(msg);
                                case 1 -> JUL_LOGGER.fine(msg);
                                case 2 -> JUL_LOGGER.info(msg);
                                case 3 -> JUL_LOGGER.warning(msg);
                                case 4 -> JUL_LOGGER.severe(msg);
                            }
                        }
                        case 2 -> { // Log4j
                            switch (levelInt) {
                                case 0 -> LOG4J_LOGGER.trace(msg);
                                case 1 -> LOG4J_LOGGER.debug(msg);
                                case 2 -> LOG4J_LOGGER.info(msg);
                                case 3 -> LOG4J_LOGGER.warn(msg);
                                case 4 -> LOG4J_LOGGER.error(msg);
                            }
                        }
                        case 3 -> { // JCL
                            switch (levelInt) {
                                case 0 -> JCL_LOGGER.trace(msg);
                                case 1 -> JCL_LOGGER.debug(msg);
                                case 2 -> JCL_LOGGER.info(msg);
                                case 3 -> JCL_LOGGER.warn(msg);
                                case 4 -> JCL_LOGGER.error(msg);
                            }
                        }
                    }
                }
            }, "Logger-Thread-" + implementation);
            thread.setDaemon(true);
            thread.start();
        }
    }
}
