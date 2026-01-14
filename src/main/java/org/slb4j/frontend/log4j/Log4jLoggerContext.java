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

import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a context implementation for managing and retrieving Log4j loggers.
 */
public final class Log4jLoggerContext implements LoggerContext {
    private static final Object EXTERNAL_CONTEXT = new Object();

    private final Map<String, ExtendedLogger> loggers = new ConcurrentHashMap<>();

    /**
     * Default constructor for the Log4jLoggerContext class.
     */
    public Log4jLoggerContext() {
        // nothing to do
    }

    @Override
    public Object getExternalContext() {
        return EXTERNAL_CONTEXT;
    }

    @Override
    public ExtendedLogger getLogger(String name) {
        return loggers.computeIfAbsent(name, LoggerLog4j::new);
    }

    @Override
    public ExtendedLogger getLogger(String name, org.apache.logging.log4j.message.@Nullable MessageFactory messageFactory) {
        return getLogger(name);
    }

    @Override
    public boolean hasLogger(String name) {
        return loggers.containsKey(name);
    }

    @Override
    public boolean hasLogger(String name, org.apache.logging.log4j.message.@Nullable MessageFactory messageFactory) {
        return hasLogger(name);
    }

    @Override
    public boolean hasLogger(String name, Class<? extends org.apache.logging.log4j.message.MessageFactory> messageFactoryClass) {
        return hasLogger(name);
    }
}
