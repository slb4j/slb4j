package org.slb4j;

public interface PatternConfigurable {
    /**
     * Set the format pattern.
     * @param logPattern the format pattern
     */
    void setPattern(LogPattern logPattern);

    /**
     * Get the format pattern.
     * @return the format pattern
     */
    LogPattern getPattern();
}
