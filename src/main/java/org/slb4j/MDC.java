package org.slb4j;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Interface representing a Mapping Diagnostic Context (MDC).
 * <p>
 * An MDC is used to store contextual information that can be associated with log events
 * to provide additional context for diagnostic purposes. This interface provides a common
 * method to access information from different MDC providers.
 */
public interface MDC {
    /**
     * Retrieves the value associated with the specified key in the
     * Mapping Diagnostic Context (MDC).
     *
     * @param key the key used to retrieve the associated value.
     *            Must not be null.
     * @return the value associated with the specified key, or null if
     *         no such key exists in the MDC.
     */
    @Nullable String get(String key);

    /**
     * Returns a stream of all key-value pairs currently stored in the Mapping Diagnostic Context (MDC).
     * Each entry in the stream represents an individual key-value pair from the MDC, where the key
     * is a contextual identifier and the value is its associated information.
     *
     * @return a sequential {@code Stream} of {@code Map.Entry<String, String>} containing
     *         all key-value pairs in the MDC. The stream reflects the state of the MDC
     *         at the time of its creation.
     */
    Stream<Map.Entry<String, String>> stream();
}
