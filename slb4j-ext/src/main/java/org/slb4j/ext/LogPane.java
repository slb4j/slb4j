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
package org.slb4j.ext;

import org.slb4j.LogLayout;
import org.slb4j.layout.PatternLayout;

/**
 * Common interface for log pane components.
 */
public interface LogPane {

    /**
     * The default pattern used for formatting log entries.
     */
    final LogLayout DEFAULT_LAYOUT = PatternLayout.parseLog4jPattern(
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %marker %logger{36} [%X]%n%n%msg%n%n%throwable%n"
    );

    /**
     * Retrieves the current LogLayout instance used for formatting log entries in this LogPane's detail area.
     *
     * @return the LogLayout instance configuring the log formatting behavior
     */
    LogLayout getLogLayout();

    /**
     * Sets the LogLayout instance to be used for formatting log entries in this LogPane's detail area.
     *
     * @param layout the LogLayout instance that defines the formatting of log entries.
     *               If null, the default layout may be applied or formatting may not occur.
     */
    void setLogLayout(LogLayout layout);

    /**
     * Retrieves the LogBuffer associated with this LogPane.
     *
     * @return the LogBuffer instance used by this LogPane
     */
    LogBuffer getLogBuffer();

    /**
     * Toggles the dark mode display setting for the log pane.
     *
     * @param dark a boolean value indicating whether dark mode should be enabled.
     *             If true, dark mode is enabled; if false, dark mode is disabled.
     */
    void setDarkMode(boolean dark);

    /**
     * Checks whether dark mode is enabled for the log pane.
     *
     * @return true if dark mode is enabled, otherwise false.
     */
    boolean isDarkMode();

}
