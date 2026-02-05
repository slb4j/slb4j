package org.slb4j.layout;

import java.util.Optional;
import java.util.function.Function;

/**
 * Enumeration of the suported standard Layouts.
 */
public enum StandardLayout {
    /** CSV layout. */
    CSV("CsvLayout", CsvLayoutBuilder::new),

    /** XML layout. */
    XML("XmlLayout", XmlLayoutBuilder::new),

    /** YAML layout. */
    YAML("YamlLayout", YamlLayoutBuilder::new),

    /** JSON layout. */
    JSON("JsonLayout", JsonLayoutBuilderLog4j::new),

    /** Log4J SimpleLayout. */
    SIMPLE_LAYOUT("SimpleLayout", SimpleLayoutBuilder::new),

    /** Pattern layout. */
    PATTERN_LAYOUT("PatternLayout", PatternLayoutBuilderLog4j:: new);

    private final String type;
    private final Function<String, LayoutBuilder> builderFactory;

    StandardLayout(String type, Function<String, LayoutBuilder> builderFactory) {
        this.type = type;
        this.builderFactory = builderFactory;
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
     * Retrieves the factory function that creates instances of {@link LayoutBuilder}.
     * <p>
     * The factory function maps a layout type string to a corresponding {@link LayoutBuilder}
     * implementation.
     *
     * @return a {@link Function} that takes a layout type string as input and returns a matching
     *         {@link LayoutBuilder} instance.
     */
    public Function<String, LayoutBuilder> builderFactory() {
        return builderFactory;
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
