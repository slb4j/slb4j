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
package org.slb4j.frontend.jcl;

import org.slb4j.LogLevel;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.apache.commons.logging.Log;

/**
 * LoggerJcl is an implementation of the Apache commons Log interfac that forwards all logging
 * calls to the global universal dispatcher instance.
 */
public final class LoggerJcl implements Log {
    private static final UniversalDispatcher DISPATCHER = UniversalDispatcher.getInstance();

    private final String name;

    /**
     * Creates an instance of the LoggerJcl class with the specified logger name.
     *
     * @param name the name of the logger
     */
    public LoggerJcl(String name) {
        this.name = name;
    }

    @Override
    public void debug(Object message) {
        DISPATCHER.dispatchJcl(name, LogLevel.DEBUG, message, null);
    }

    @Override
    public void debug(Object message, Throwable t) {
        DISPATCHER.dispatchJcl(name, LogLevel.DEBUG, message, t);
    }

    @Override
    public void error(Object message) {
        DISPATCHER.dispatchJcl(name, LogLevel.ERROR, message, null);
    }

    @Override
    public void error(Object message, Throwable t) {
        DISPATCHER.dispatchJcl(name, LogLevel.ERROR, message, t);
    }

    @Override
    public void fatal(Object message) {
        DISPATCHER.dispatchJcl(name, LogLevel.ERROR, message, null);
    }

    @Override
    public void fatal(Object message, Throwable t) {
        DISPATCHER.dispatchJcl(name, LogLevel.ERROR, message, t);
    }

    @Override
    public void info(Object message) {
        DISPATCHER.dispatchJcl(name, LogLevel.INFO, message, null);
    }

    @Override
    public void info(Object message, Throwable t) {
        DISPATCHER.dispatchJcl(name, LogLevel.INFO, message, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return DISPATCHER.isEnabled(name, LogLevel.DEBUG, null);
    }

    @Override
    public boolean isErrorEnabled() {
        return DISPATCHER.isEnabled(name, LogLevel.ERROR, null);
    }

    @Override
    public boolean isFatalEnabled() {
        return DISPATCHER.isEnabled(name, LogLevel.ERROR, null);
    }

    // Implement warn, debug, trace, fatal similarly...
    @Override
    public boolean isInfoEnabled() {
        return DISPATCHER.isEnabled(name, LogLevel.INFO, null);
    }

    @Override
    public boolean isTraceEnabled() {
        return DISPATCHER.isEnabled(name, LogLevel.TRACE, null);
    }

    @Override
    public boolean isWarnEnabled() {
        return DISPATCHER.isEnabled(name, LogLevel.WARN, null);
    }

    @Override
    public void trace(Object message) {
        DISPATCHER.dispatchJcl(name, LogLevel.TRACE, message, null);
    }

    @Override
    public void trace(Object message, Throwable t) {
        DISPATCHER.dispatchJcl(name, LogLevel.TRACE, message, t);
    }

    @Override
    public void warn(Object message) {
        DISPATCHER.dispatchJcl(name, LogLevel.WARN, message, null);
    }

    @Override
    public void warn(Object message, Throwable t) {
        DISPATCHER.dispatchJcl(name, LogLevel.WARN, message, t);
    }

}
