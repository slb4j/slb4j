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
package org.slb4j.support;

import org.slb4j.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StackWalkerLocationResolverTest {

    @Test
    void testResolve() {
        // loggerClassName is Infra, infraPackage is also Infra
        StackWalkerLocationResolver resolver = new StackWalkerLocationResolver(
                Infra.class.getName(),
                "org.slb4j.support.StackWalkerLocationResolverTest$Infra"
        );

        Location location = Infra.call(resolver);

        assertNotNull(location);
        assertEquals(StackWalkerLocationResolverTest.class.getName(), location.getClassName());
        assertEquals("testResolve", location.getMethodName());
    }

    @Test
    void testResolveWithOtherInfra() {
        // Logger is Infra, but it calls OtherInfra which is also part of infraPackage
        StackWalkerLocationResolver resolver = new StackWalkerLocationResolver(
                Infra.class.getName(),
                "org.slb4j.support.StackWalkerLocationResolverTest$"
        );

        Location location = Infra.callOther(resolver);

        assertNotNull(location);
        assertEquals(StackWalkerLocationResolverTest.class.getName(), location.getClassName());
        assertEquals("testResolveWithOtherInfra", location.getMethodName());
    }

    @Test
    void testResolveWithInternalFramesBeforeInfra() {
        // NotInfra.callInfra calls Infra.call, which calls resolver.resolve()
        // The logger class is Infra.
        // It should find NotInfra.callInfra as the caller.
        StackWalkerLocationResolver resolver = new StackWalkerLocationResolver(
                Infra.class.getName(),
                "org.slb4j.support.StackWalkerLocationResolverTest$Infra"
        );

        Location location = NotInfra.callInfra(resolver);

        assertNotNull(location);
        assertEquals(NotInfra.class.getName(), location.getClassName());
        assertEquals("callInfra", location.getMethodName());
    }

    @Test
    void testResolveNoLoggerFound() {
        StackWalkerLocationResolver resolver = new StackWalkerLocationResolver(
                "non.existent.Logger",
                "org.slb4j"
        );

        assertThrows(IllegalStateException.class, resolver::resolve);
    }

    static final class NotInfra {
        private NotInfra() {}

        static Location callInfra(StackWalkerLocationResolver resolver) {
            return Infra.call(resolver);
        }
    }

    static final class Infra {
        private Infra() {}

        static Location call(StackWalkerLocationResolver resolver) {
            return resolver.resolve();
        }

        static Location callOther(StackWalkerLocationResolver resolver) {
            return OtherInfra.call(resolver);
        }
    }

    static final class OtherInfra {
        private OtherInfra() {}

        static Location call(StackWalkerLocationResolver resolver) {
            return resolver.resolve();
        }
    }
}
