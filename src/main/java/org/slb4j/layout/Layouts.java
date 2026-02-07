package org.slb4j.layout;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Enumeration of the suported standard Layouts.
 */
public final class Layouts {

    /**
     * Utility class constructor.
     */
    private Layouts() {
        // nothing to do
    }

    private static final Map<String, Function<String, LayoutBuilder>> REGISTERED_LAYOUTS;

    static {
        REGISTERED_LAYOUTS = new ConcurrentHashMap<>();
        registerLayout("SimpleLayout", SimpleLayoutBuilder:: new);
        registerLayout("JsonLayout", JsonLayoutBuilder:: new);
        registerLayout("PatternLayout", PatternLayoutBuilder:: new);
    }

    /**
     * Registers a new layout type with the system.
     *
     * @param type The unique name of the layout to be registered. This string serves as the identifier for the layout type.
     * @param builderFactory A factory function that creates instances of {@link LayoutBuilder} for the specified layout type.
     *                       The factory function takes the layout type as input and returns the corresponding {@link LayoutBuilder}.
     * @return {@code true} if the layout type was successfully registered, or {@code false} if a layout with the same name
     *         had already been registered.
     */
    public static boolean registerLayout(String type, Function<String, LayoutBuilder> builderFactory) {
        return REGISTERED_LAYOUTS.putIfAbsent(type, builderFactory) == null;
    }

    /**
     * Retrieves an unmodifiable view of all registered layouts and their corresponding builder factories.
     * The returned map associates layout names with functions capable of constructing instances of
     * {@link LayoutBuilder}.
     *
     * @return a map where the keys are layout names (as {@code String}) and the values are functions
     *         that take a {@code String} and return a {@code LayoutBuilder} instance.
     */
    public static final Map<String, Function<String, LayoutBuilder>> getRegisteredLayouts() {
        return Collections.unmodifiableMap(REGISTERED_LAYOUTS);
    }

    /**
     * Retrieves the builder function associated with the specified layout name.
     * The builder function is a {@link Function} that accepts a {@link String} argument
     * and returns a {@link LayoutBuilder} instance. If no builder is registered for
     * the given name, an empty {@link Optional} is returned.
     *
     * @param name the name of the layout for which the builder function is requested.
     * @return an {@link Optional} containing the builder function if found, or an empty
     *         {@link Optional} if no builder is associated with the given name.
     */
    public static Optional<Function<String, LayoutBuilder>> getBuilder(String name) {
        return Optional.ofNullable(REGISTERED_LAYOUTS.get(name));
    }

    /**
     * Retrieves the builder function associated with the specified layout name.
     * If the layout name has not been registered, an {@link IllegalStateException}
     * is thrown to indicate that the layout is unsupported.
     *
     * @param name the name of the layout whose builder function is to be retrieved
     * @return a {@link Function} that takes a {@link String} and returns a {@link LayoutBuilder},
     *         which can be used to construct the desired layout
     * @throws IllegalStateException if no builder function is associated with the specified layout name
     */
    public static Function<String, LayoutBuilder> builder(String name) {
        Function<String, LayoutBuilder> builder = REGISTERED_LAYOUTS.get(name);
        if (builder == null) {
            throw new IllegalStateException("unsupported Layout: " + name);
        }
        return builder;
    }
}
