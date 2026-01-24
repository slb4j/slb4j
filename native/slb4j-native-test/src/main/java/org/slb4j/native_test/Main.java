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
        org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger("native.slf4j");
        slf4jLogger.info("Native test message from SLF4J");
    }
}
