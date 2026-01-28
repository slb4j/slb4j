package org.slb4j;

/**
 * Represents a configurable contract for setting and retrieving log pattern configurations.
 */
public interface LogPatternConfigurable {
    /**
     * Set the format pattern.
     * @param logPattern the format pattern
     */
    void setLogPattern(LogPattern logPattern);

    /**
     * Get the format pattern.
     * @return the format pattern
     */
    LogPattern getLogPattern();
}
