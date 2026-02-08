package org.slb4j.ext.layouts;

import org.slb4j.LogLayout;
import org.slb4j.layout.LayoutBuilder;

import java.util.Map;

/**
 * A builder class for creating instances of {@link CsvLayout} using Log4J2 options.
 */
public class CsvLayoutBuilder extends LayoutBuilder {

    private static final String TYPE = "type";

    private static final Map<String, LayoutAtribute> ATTRIBUTES = Map.of(
            TYPE, new LayoutAtribute(TYPE, true, null, null)
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
