package org.slb4j.layout;

import org.slb4j.LogLayout;

import java.util.Map;

/**
 * A builder class responsible for creating instances of {@link SimpleLayout}.
 */
public class SimpleLayoutBuilder extends LayoutBuilder {

    private static final Map<String, LayoutAtribute> ATTRIBUTES = Map.of(
    );

    /**
     * Constructs a new instance of {@code SimpleLayoutBuilder}.
     *
     * @param name the name of the layout to be built
     */
    SimpleLayoutBuilder(String name) {
        super(name, ATTRIBUTES);
    }

    @Override
    public LogLayout build() {
        return new SimpleLayout();
    }
}
