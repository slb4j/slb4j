/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slb4j;

import org.jspecify.annotations.Nullable;

import java.util.Map;

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
     * Returns the Mapping Diagnostic Context (MDC).
     *
     * @return the MDC. The stream reflects the state of the MDC
     *         at the time of its creation.
     */
    Map<String, String> get();
}
