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
package org.slb4j.frontend.log4j;

import org.apache.logging.log4j.spi.LoggerContext;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * A factory class for creating LogDispatcher instances using Log4j.
 */
public final class Log4jLoggerContextFactory implements org.apache.logging.log4j.spi.LoggerContextFactory {

    private static final LoggerContext CONTEXT = new Log4jLoggerContext();

    /**
     * Constructor, called by SPI.
     */
    public Log4jLoggerContextFactory() { /* nothing to do */ }

    @Override
    public LoggerContext getContext(String fqcn, @Nullable ClassLoader loader, @Nullable Object externalContext, boolean currentContext) {
        return CONTEXT;
    }

    @Override
    public LoggerContext getContext(String fqcn, @Nullable ClassLoader loader, @Nullable Object externalContext, boolean currentContext, @Nullable URI configLocation, @Nullable String name) {
        return CONTEXT;
    }

    @Override
    public void removeContext(LoggerContext context) {
        // nop
    }
}
