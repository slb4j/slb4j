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


import org.jspecify.annotations.Nullable;
import org.slb4j.LocationResolver;
import org.slb4j.LogLevel;
import org.slb4j.dispatcher.UniversalDispatcher;
import org.slb4j.support.StackWalkerLocationResolver;

import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Custom Java Util Logging (JUL) {@link Handler} implementation that dispatches log records to
 * the global {@link UniversalDispatcher}.
 * <p>
 * <strong>Note:</strong> This class filters out messages from {@code java.*}, {@code javax.*},
 * and {@code sun.*} packages with {@link Level#FINE} or below.
 */
public final class JulHandler extends Handler {

    private static final UniversalDispatcher DISPATCHER = UniversalDispatcher.getInstance();
    private static final LocationResolver LOCATION_RESOLVER = new StackWalkerLocationResolver(Logger.class, "java.util.logging");

    /**
     * Constructs a new instance of the {@code JulHandler}.
     */
    public JulHandler() {
        // nothing to do
    }

    /**
     * Translates a {@link Level} to the corresponding {@link LogLevel}.
     * This method maps the {@code Level} instances from the `java.util.logging` API
     * to the application's internal {@code LogLevel} enumeration.
     *
     * @param level the {@code java.util.logging.Level} to be translated; must not be null
     * @return the {@code LogLevel} equivalent of the provided {@code java.util.logging.Level}
     */
    public static LogLevel translateJulLevel(Level level) {
        int val = level.intValue();
        if (val <= Level.FINEST.intValue()) return LogLevel.TRACE;
        if (val <= Level.FINE.intValue()) return LogLevel.DEBUG;
        if (val <= Level.INFO.intValue()) return LogLevel.INFO;
        if (val <= Level.WARNING.intValue()) return LogLevel.WARN;
        return LogLevel.ERROR;
    }

    /**
     * Formats a message pattern using the provided parameters. If the parameters are null
     * or empty, the raw pattern is returned. In case of a formatting error, the method
     * falls back to returning the raw pattern.
     *
     * @param pattern the message pattern to be formatted; must not be null
     * @param params the parameters to replace the placeholders in the pattern; may be null
     *               or an empty array
     * @return the formatted message if formatting is successful, or the raw pattern if
     *         no parameters are provided or an error occurs during formatting
     */
    public static Supplier<String> formatJulMessage(String pattern, @Nullable Object @Nullable [] params) {
        if (pattern.indexOf('{') != -1 || pattern.indexOf('\'') != -1) {
            return () -> java.text.MessageFormat.format(pattern, params);
        }
        return pattern::toString;
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
            LogLevel lvl = translateJulLevel(logRecord.getLevel());
            if (DISPATCHER.isLevelEnabled(lvl)) {
                String loggerName1 = logRecord.getLoggerName();
                Supplier<String> msg = formatJulMessage(logRecord.getMessage(), logRecord.getParameters());
                Throwable t = logRecord.getThrown();

                DISPATCHER.filterAndDispatch(logRecord.getMillis(), loggerName1, lvl, null, null, LOCATION_RESOLVER, msg, t);
            }
        }
    }

    @Override
    public void flush() { /* nothing to do */ }

    @Override
    public void close() throws SecurityException { /* nothing to do */ }
}