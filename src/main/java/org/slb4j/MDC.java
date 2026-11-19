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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
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
     * An MDC implementation that contains no diagnostic context entries.
     *
     * This instance always reports itself as empty and returns an empty map for
     * the complete context.
     */
    MDC EMPTY = new MDC() {
        @Override
        public @Nullable String get(String key) {

            return "";
        }

        @Override
        public Map<String, String> get() {
            return Collections.emptyMap();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    };

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

    /**
     * Determines whether the Mapping Diagnostic Context contains no entries.
     *
     * @return true if the MDC contains no entries, false otherwise
     */
    default boolean isEmpty() {
        return get().isEmpty();
    }

    /**
     * Creates an MDC adapter backed by the first supported diagnostic context implementation
     * available on the classpath.
     *
     * The lookup order is Log4j ThreadContext, then SLF4J MDC. If neither implementation is
     * available or usable, a fallback MDC implementation is returned that contains no data.
     *
     * @return an MDC adapter for the available diagnostic context implementation, or a fallback
     *         MDC implementation when none is available
     */
    @SuppressWarnings("unchecked")
    static MDC createMdc() {
        // try org.apache.logging.log4j.ThreadContext
        try {
            Class<?> threadContextClass = Class.forName("org.apache.logging.log4j.ThreadContext");
            Method get = threadContextClass.getMethod("get", String.class);
            Method getImmutableContext = threadContextClass.getMethod("getImmutableContext");

            return new MDC() {
                @Override
                public @Nullable String get(String key) {
                    try {
                        return (String) get.invoke(null, key);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        SLB4J.logInternal(LogLevel.WARN,"could not get value for key " + key + " from ThreadContext", e);
                        return null;
                    }
                }

                @Override
                public Map<String, String> get() {
                    try {
                        return (Map<String, String>) getImmutableContext.invoke(null);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        SLB4J.logInternal(LogLevel.WARN,"could not get immutable context", e);
                        return Collections.emptyMap();
                    }
                }
            };
        } catch (ClassNotFoundException e) {
            SLB4J.logInternal(LogLevel.DEBUG,"org.apache.logging.log4j.ThreadContext is not on classpath", e);
        } catch (NoSuchMethodException e) {
            SLB4J.logInternal(LogLevel.WARN,"method not found in ThreadContext", e);
        }

        // try org.slf4j.MDC
        try {
            Class<?> threadContextClass = Class.forName("org.slf4j.MDC");
            Method get = threadContextClass.getMethod("get", String.class);
            Method getCopyOfContextMap = threadContextClass.getMethod("getCopyOfContextMap");

            return new MDC() {
                @Override
                public @Nullable String get(String key) {
                    try {
                        return (String) get.invoke(null, key);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        SLB4J.logInternal(LogLevel.WARN,"could not get MDC value for key " + key, e);
                        return null;
                    }
                }

                @Override
                public Map<String, String> get() {
                    try {
                        Map<String, String> contextMap = (Map<String, String>) getCopyOfContextMap.invoke(null);
                        return contextMap != null ? contextMap : Collections.emptyMap();
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        SLB4J.logInternal(LogLevel.WARN,"could not get context map", e);
                        return Collections.emptyMap();
                    }
                }
            };
        } catch (ClassNotFoundException e) {
            SLB4J.logInternal(LogLevel.DEBUG,"org.slf4j.MDC is not on classpath", e);
        } catch (NoSuchMethodException e) {
            SLB4J.logInternal(LogLevel.WARN,"method not found in MDC", e);
        }

        // return an empty MDC
        return EMPTY;
    }
}
