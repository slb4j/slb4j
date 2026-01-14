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
package org.slb4j.frontend.jul;


import org.slb4j.dispatcher.UniversalDispatcher;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Custom Java Util Logging (JUL) {@link Handler} implementation that dispatches log records to
 * the global {@link UniversalDispatcher}.
 * <p>
 * <strong>Note:</strong> This class filters out messages from {@code java.*}, {@code javax.*},
 * and {@code sun.*} packages with {@link Level#FINE} or below.
 */
public final class JulHandler extends Handler {

    private static final UniversalDispatcher DISPATCHER = UniversalDispatcher.getInstance();

    /**
     * Constructs a new instance of the {@code JulHandler}.
     */
    public JulHandler() {
        // nothing to do
    }

    @Override
    public void publish(LogRecord logRecord) {
        // filter out Java messages with FINE level coming in over JUL
        // these are usually not of interest and when these are handled, they
        // often trigger other message while being processed leading to a DOS situation
        String loggerName = logRecord.getLoggerName();
        if (logRecord.getLevel().intValue() > Level.FINE.intValue() || (
                !loggerName.startsWith("java.")
                        && !loggerName.startsWith("javax.")
                        && !loggerName.startsWith("sun.")
        )) {
            DISPATCHER.dispatchJul(logRecord);
        }
    }

    @Override
    public void flush() { /* nothing to do */ }

    @Override
    public void close() throws SecurityException { /* nothing to do */ }
}