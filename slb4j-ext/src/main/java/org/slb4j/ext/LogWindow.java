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

/**
 * Common interface for log window components.
 */
public interface LogWindow {
    /**
     * Retrieves the LogBuffer associated with this LogWindow.
     *
     * @return the LogBuffer instance used by this LogWindow
     */
    LogBuffer getLogBuffer();

    /**
     * Retrieves the LogPane instance used by this LogWindow.
     *
     * @return the LogPane instance associated with this LogWindow
     */
    LogPane getLogPane();

    /**
     * Sets the dark mode display setting for the log window.
     *
     * @param darkMode a boolean value indicating whether dark mode should be enabled.
     *                 If true, dark mode is enabled; if false, dark mode is disabled.
     */
    void setDarkMode(boolean darkMode);
}
