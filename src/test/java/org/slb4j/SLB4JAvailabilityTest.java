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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SLB4JAvailabilityTest {

    @BeforeAll
    static void initializeSlb4j() {
        SLB4J.init();
    }

    @Test
    void testIsSlb4jExtAvailableReflectsClasspath() {
        assertAvailability(SLB4J::isSlb4jExtAvailable, "org.slf4j.ext.LogBufer");
    }

    @Test
    void testIsSlb4jExtFxAvailableReflectsClasspath() {
        assertAvailability(SLB4J::isSlb4jExtFxAvailable, "org.slb4j.ext.fx.FxLogPane");
    }

    @Test
    void testIsSlb4jExtSwingAvailableReflectsClasspath() {
        assertAvailability(SLB4J::isSlb4jExtSwingAvailable, "org.slb4j.ext.swing.SwingLogPane");
    }

    private static void assertAvailability(BooleanSupplier availabilityCheck, String className) {
        withContextClassLoader(classpathWith(className), () ->
                assertTrue(availabilityCheck.getAsBoolean(), "Expected class to be detected: " + className)
        );
        withContextClassLoader(classpathWith(), () ->
                assertFalse(availabilityCheck.getAsBoolean(), "Expected class to be absent: " + className)
        );
    }

    private static ClassLoader classpathWith(String... availableClasses) {
        Set<String> availableResources = Arrays.stream(availableClasses)
                .map(className -> className.replace('.', '/') + ".class")
                .collect(Collectors.toSet());
        URL markerResource = Objects.requireNonNull(
                SLB4JAvailabilityTest.class.getResource("SLB4JAvailabilityTest.class"),
                "Marker resource not found"
        );
        return new ClassLoader(null) {
            @Override
            public @Nullable URL getResource(String name) {
                return availableResources.contains(name) ? markerResource : null;
            }
        };
    }

    private static void withContextClassLoader(ClassLoader classLoader, Runnable assertions) {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            assertions.run();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }
}
