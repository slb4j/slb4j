package org.slb4j.ext;

/**
 * Common interface for log pane components.
 */
public interface LogPane {
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
}
