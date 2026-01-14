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
package org.slb4j.frontend.slf4j;

import org.slf4j.ILoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The LoggerFactorySlf4j class is an implementation of the ILoggerFactory and LogDispatcher interfaces.
 */
public final class LoggerFactorySlf4j implements ILoggerFactory {

    private final ConcurrentHashMap<String, LoggerSlf4j> loggers = new ConcurrentHashMap<>();

    /**
     * Constructs a new instance of LoggerFactorySlf4j.
     */
    public LoggerFactorySlf4j() {
        // nothing to do
    }

    @Override
    public org.slf4j.Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, LoggerSlf4j::new);
    }
}
