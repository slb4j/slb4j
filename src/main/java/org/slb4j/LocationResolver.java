package org.slb4j;

import org.jspecify.annotations.Nullable;

/**
 * A functional interface for resolving a {@link Location}.
 * <p>
 * Implementations of this interface determine a specific point in the code to
 * identify the source of a log message.
 * <p>
 * The {@link #resolve()} method provides the mechanism for obtaining such contextual
 * information. It may return {@code null} if no relevant location can be determined.
 */
@FunctionalInterface
public interface LocationResolver {
    /**
     * Resolves the location in the code where this method is invoked.
     * Implementations of this method provide information about the class,
     * method, file, and line number for contextual identification.
     *
     * @return a {@link Location} instance representing the resolved
     *         location, or {@code null} if the location cannot be determined.
     */
    @Nullable Location resolve();
}
