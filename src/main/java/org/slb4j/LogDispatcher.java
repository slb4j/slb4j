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
package org.slb4j;

import java.util.Collection;

/**
 * This interface defines the contract for classes that dispatch log entries to registered handlers.
 */
public interface LogDispatcher {
    /**
     * Adds a handler for log entry events. The handler will be invoked
     * whenever a log entry is received.
     *
     * @param handler The log entry handler to be added.
     */
    void addLogHandler(LogHandler handler);

    /**
     * Removes a previously added log entry handler. The handler will no longer be invoked
     * for any log entries.
     *
     * @param handler The log entry handler to be removed.
     */
    void removeLogHandler(LogHandler handler);

    /**
     * Sets the {@link LogFilter} for log entry events.
     *
     * <p>Only entries that pass the filter will be dispatched to handlers.
     *
     * @param filter The filter to be set for log entry events.
     */
    void setFilter(LogFilter filter);

    /**
     * Get the {@link LogFilter}.
     *
     * @return the filter in use
     */
    LogFilter getFilter();

    /**
     * Get the registered log entry handlers. Note that implementations usually hold weak references
     * to the handlers, so unused handlers may already have been removed from the list.
     * @return collection containing the registered log entry handlers
     */
    Collection<LogHandler> getLogHandlers();
}
