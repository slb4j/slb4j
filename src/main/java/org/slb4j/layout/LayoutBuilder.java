package org.slb4j.layout;

import org.jspecify.annotations.Nullable;
import org.slb4j.LogLayout;
import org.slb4j.support.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Abstract base class for building specific types of logging layouts.
 * <p>
 * A layout builder is responsible for constructing instances of {@link LogLayout}
 * based on a set of attributes and their corresponding values.
 */
public abstract class LayoutBuilder {

    /**
     * Represents an attribute of a logging layout with associated metadata.
     *
     * @param attribute     The name of the layout attribute.
     * @param isSupported   Indicates whether the attribute is supported by LB4J for the layout.
     * @param defaultValue  The default value of the attribute as a string.
     * @param converter     A function to convert string values into their corresponding object representation.
     */
    record LayoutAtribute(
            String attribute,
            boolean isSupported,
            @Nullable String defaultValue,
            Function<String, Object> converter
    ) {
        public @Nullable Object defaultValueAsObject() {
            return defaultValue == null ? null : converter.apply(defaultValue);
        }
    }

    private final String layoutName;
    private final Map<String, LayoutAtribute> attributes;
    private final Map<String, Object> values;

    /**
     * Constructs a new instance of the LayoutBuilder class with the specified layout name and attributes.
     *
     * @param layoutName The name of the layout being constructed.
     * @param attributes A map of layout attributes where the key is the attribute name and the value is a {@link LayoutAtribute}
     *                   object describing the attribute's properties, such as its default value and conversion function.
     */
    protected LayoutBuilder(String layoutName, Map<String, LayoutAtribute> attributes) {
        this.layoutName = layoutName;
        this.attributes = attributes;
        this.values = new HashMap<>();
    }

    /**
     * Applies a set of attribute definitions to the layout being constructed. The method updates
     * the internal attribute values based on the provided definitions. If an attribute is unknown,
     * unsupported, or has an invalid value, appropriate warning messages are displayed, and default
     * values are used when applicable.
     *
     * @param defs A map of attribute definitions where the key is the attribute name and the value
     *             is the provided value for that attribute. The map is used to update the layout's
     *             attributes and their corresponding values.
     */
    public void applyDefinitions(Map<String, String> defs) {
        defs.forEach((attributeName, value) -> {
            LayoutAtribute attribute = attributes.get(attributeName);
            if (attribute == null) {
                Util.err().format("Ignoring unknown attribute %s in definition of layout %s!%n", attributeName, layoutName);
                return;
            }
            if (!attribute.isSupported()) {
                if (attribute.defaultValue() == null) {
                    Util.err().format("Ignoring unsupported attribute %s in definition of layout %s!%n", attributeName, layoutName);
                    return;
                }
                if (!Objects.equals(attribute.defaultValue(), value)) {
                    Util.err().format("Unsupported value for attribute %s in definition of layout %s, using default %s instead of supplied value %s!%n", attributeName, layoutName, attribute.defaultValue(), value);
                }
                value = attribute.defaultValue();
            }
            values.put(attributeName, attribute.converter().apply(value));
        });
    }

    /**
     * Retrieves the value associated with the given key from the internal values map.
     * If the key is not present in the values map, the default value from the attributes
     * map is returned.
     *
     * @param key The key whose associated value is to be retrieved. It serves as the identifier
     *            in both the values and attributes maps.
     * @return The value associated with the specified key from the values map, or the default
     *         value from the attributes map if the key is not found in the values map.
     */
    protected Object getValue(String key) {
        Object value = values.get(key);
        return value != null ? value : attributes.get(key).defaultValueAsObject();
    }

    /**
     * Builds and returns a fully constructed {@link LogLayout} instance based on the
     * configuration specified in the implementing class. This method encapsulates
     * any transformations, validations, or custom logic required to create the
     * desired implementation of the {@code LogLayout} interface.
     *
     * @return a {@link LogLayout} instance representing the desired layout configuration
     */
    public abstract LogLayout build();
}
