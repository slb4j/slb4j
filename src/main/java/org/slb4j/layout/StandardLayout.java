package org.slb4j.layout;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Enumeration of the suported standard Layouts.
 */
public enum StandardLayout {
    /** CSV layout. */
    CSV("CsvLayout"),

    /** XML layout. */
    XML("XmlLayout"),

    /** JSON layout. */
    JSON("JsonLayout"),

    /** Log4J SimpleLayout. */
    LOG4J_SIMPLE_LAYOUT("SimpleLayout"),

    /** Pattern layout. */
    PATTERN_LAYOUT("PatternLayout");

    private final String type;

    StandardLayout(String type) {
        this.type = type;
    }

    /**
     * Return the type string of the layout as in the log4j2 properties file.
     *
     * @return the type string
     */
    public String type() {
        return type;
    }

    /**
     * Retrieves the {@link StandardLayout} corresponding to the given type string, if it exists.
     * The type comparison is case-insensitive.
     *
     * @param type the type string to search for, representing a standard layout.
     * @return an {@link Optional} containing the matching {@link StandardLayout}, or an empty {@link Optional} if no match is found.
     */
    public static Optional<StandardLayout> forType(String type) {
        for (StandardLayout layout : values()) {
            if (layout.type.equalsIgnoreCase(type)) {
                return Optional.of(layout);
            }
        }
        return Optional.empty();
    }
}
