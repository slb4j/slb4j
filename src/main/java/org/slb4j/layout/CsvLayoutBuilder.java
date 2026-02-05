package org.slb4j.layout;

import org.slb4j.LogLayout;

import java.util.Map;

/**
 * A builder class for creating instances of {@link CsvLayout} using Log4J2 options.
 */
public class CsvLayoutBuilder extends LayoutBuilder {

    private static final Map<String, LayoutAtribute> ATTRIBUTES = Map.of(
    );

    /**
     * Constructs a new instance of {@code CsvLayoutBuilder}.
     *
     * @param name the name of the layout to be built
     */
    CsvLayoutBuilder(String name) {
        super(name, ATTRIBUTES);
    }

    @Override
    public LogLayout build() {
        return new CsvLayout();
    }
}
