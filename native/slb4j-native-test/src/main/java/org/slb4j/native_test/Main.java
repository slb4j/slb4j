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
package org.slb4j.native_test;

import org.slb4j.SLB4J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class Main {
    static {
        SLB4J.init();
    }

    private Main() {}

    public static void main(String[] args) {
        // JUL
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger("native.jul");
        julLogger.info("Native test message from JUL");

        // JCL
        org.apache.commons.logging.Log jclLogger = org.apache.commons.logging.LogFactory.getLog("native.jcl");
        jclLogger.info("Native test message from JCL");

        // Log4j
        org.apache.logging.log4j.Logger log4jLogger = org.apache.logging.log4j.LogManager.getLogger("native.log4j");
        log4jLogger.info("Native test message from Log4j");

        // SLF4J
        Logger slf4jLogger = LoggerFactory.getLogger("native.slf4j");
        slf4jLogger.info("Native test message from SLF4J");

        // Advanced logging to test filters and patterns
        Logger specialLogger = LoggerFactory.getLogger("org.slb4j.native_test.special");
        specialLogger.trace("This is a TRACE message from a special logger (should be visible)");
        specialLogger.debug("This is a DEBUG message from a special logger (should be visible)");

        Logger restrictedLogger = LoggerFactory.getLogger("org.slb4j.native_test.restricted");
        restrictedLogger.info("This is an INFO message from a restricted logger (should NOT be visible)");
        restrictedLogger.warn("This is a WARN message from a restricted logger (should be visible)");

        Logger normalLogger = LoggerFactory.getLogger("org.slb4j.native_test.Normal");
        normalLogger.debug("This is a DEBUG message from a normal logger (should NOT be visible due to levelrule=INFO)");
        normalLogger.info("This is an INFO message from a normal logger (should be visible)");

        // Test Markers
        Marker importantMarker = MarkerFactory.getMarker("IMPORTANT");
        slf4jLogger.info(importantMarker, "Message with Marker");

        // Test Exceptions
        try {
            throw new RuntimeException("Test exception");
        } catch (RuntimeException e) {
            slf4jLogger.error("Caught an exception", e);
        }

        // Generate some traffic for rotation
        Logger trafficLogger = LoggerFactory.getLogger("traffic");
        for (int i = 0; i < 20; i++) {
            trafficLogger.info("Traffic message number " + i + " - padding with some text to reach 1KB sooner. Lorem ipsum dolor sit amet, consectetur adipiscing elit.");
        }
    }
}
