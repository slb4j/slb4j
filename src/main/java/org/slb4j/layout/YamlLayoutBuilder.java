package org.slb4j.layout;

import org.slb4j.LogLayout;

import java.time.ZoneOffset;
import java.util.Map;

/**
 * A builder class for creating instances of {@link YamlLayout} using Log4J2 options.
 */
public class YamlLayoutBuilder extends LayoutBuilder {

    private static final Map<String, LayoutAtribute> ATTRIBUTES = Map.of(
    );

    /**
     * Constructs a new instance of {@code YamlLayoutBuilder}.
     *
     * @param name the name of the layout to be built
     */
    YamlLayoutBuilder(String name) {
        super(name, ATTRIBUTES);
    }

    @Override
    public LogLayout build() {
        return new YamlLayout(ZoneOffset.UTC);
    }
}
