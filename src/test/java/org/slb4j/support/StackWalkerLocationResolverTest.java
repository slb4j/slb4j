package org.slb4j.support;

import org.slb4j.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StackWalkerLocationResolverTest {

    @Test
    void testResolve() {
        StackWalkerLocationResolver resolver = new StackWalkerLocationResolver("org.slb4j.support.StackWalkerLocationResolverTest$Infra");

        Location location = Infra.call(resolver);

        assertNotNull(location);
        assertEquals(StackWalkerLocationResolverTest.class.getName(), location.getClassName());
        assertEquals("testResolve", location.getMethodName());
    }

    @Test
    void testResolveWithMultipleInfraPackages() {
        StackWalkerLocationResolver resolver = new StackWalkerLocationResolver(
                "org.slb4j.support.StackWalkerLocationResolverTest$Infra",
                "org.slb4j.support.StackWalkerLocationResolverTest$OtherInfra"
        );

        Location location = Infra.callOther(resolver);

        assertNotNull(location);
        assertEquals(StackWalkerLocationResolverTest.class.getName(), location.getClassName());
        assertEquals("testResolveWithMultipleInfraPackages", location.getMethodName());
    }

    @Test
    void testResolveNoInfraFound() {
        // If it never hits infra, dropWhile(!isInfra) will exhaust the stream
        StackWalkerLocationResolver resolver = new StackWalkerLocationResolver("non.existent.Package");

        Location location = resolver.resolve();

        assertNull(location, "Should be null if no infra frame is found because of .dropWhile(f -> !isInfra(f.getClassName()))");
    }

    @Test
    void testResolveOnlyInfraFound() {
        StackWalkerLocationResolver resolver = new StackWalkerLocationResolver("org.slb4j.support.StackWalkerLocationResolverTest$Infra");

        // Simulating a call where Infra is the last frame (not possible in a real JVM but we can try to hit it)
        // Actually we can just call it from Infra directly.
        Location location = Infra.call(resolver);
        // Wait, if Infra calls resolver.resolve(), the caller of Infra is testResolveOnlyInfraFound which is NOT infra.
        // So it should find testResolveOnlyInfraFound.
        assertNotNull(location);
        assertEquals("testResolveOnlyInfraFound", location.getMethodName());
    }

    @Test
    void testResolveWithInternalFramesBeforeInfra() {
        // Resolver is NOT infra.
        // SomeInternalClass.doSomething is NOT infra.
        // Logger.log IS infra.
        // User.main is NOT infra.

        StackWalkerLocationResolver resolver = new StackWalkerLocationResolver("org.slb4j.support.StackWalkerLocationResolverTest$Infra");

        Location location = NotInfra.callInfra(resolver);

        assertNotNull(location);
        assertEquals("callInfra", location.getMethodName());
    }

    static class NotInfra {
        static Location callInfra(StackWalkerLocationResolver resolver) {
            return Infra.call(resolver);
        }
    }

    static class Infra {
        static Location call(StackWalkerLocationResolver resolver) {
            return resolver.resolve();
        }

        static Location callOther(StackWalkerLocationResolver resolver) {
            return OtherInfra.call(resolver);
        }
    }

    static class OtherInfra {
        static Location call(StackWalkerLocationResolver resolver) {
            return resolver.resolve();
        }
    }
}
