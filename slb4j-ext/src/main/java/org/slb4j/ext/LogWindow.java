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
}
