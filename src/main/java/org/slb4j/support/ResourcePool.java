package org.slb4j.support;

import org.jspecify.annotations.Nullable;
import org.slb4j.LogLevel;
import org.slb4j.SLB4J;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Represents a pool of reusable resources for maintaining efficient resource management.
 *
 * @param <T> the type of resource managed by the pool
 */
public interface ResourcePool<T> {
    /**
     * Represents a lease on a managed resource, providing controlled access with lifecycle management.
     *
     * @param <T> the type of the resource managed by the lease
     */
    interface Lease<T> extends AutoCloseable {
        /**
         * Get the managed resource.
         *
         * @return the managed resource
         */
        T get();

        /**
         * Resets the resource for the next use and returns it to the pool.
         **/
        @Override
        void close();
    }

    /**
     * Creates a new resource pool where resources are managed on a per-thread basis.
     * <p>
     * Each thread has its own instance of the resource, which is created using the provided factory
     * and released using the provided releaser when necessary.
     * <p>
     * <strong>Notes</strong>
     * <ul>
     * <li>This pool works exclusively on a per-thread basis and is not re-entrant safe for multiple
     * accesses from the same thread.
     * <li>When thread pools are used, instances from the pool can be reused and might never be cleaned
     *     up. It is the user's responsibility to ensure no data is leaked by not cleaning up resources
     *     after use.
     * </ul>
     *
     * @param <T>      the type of resource managed by the pool
     * @param factory  a {@code Supplier} that creates a new resource when one is needed
     * @param releaser a {@code Consumer} that handles the cleanup or release of the resource
     * @return a {@code ResourcePool} instance for managing thread-local resources
     */
    static <T> ResourcePool<T> newThreadBasedResourcePool(Supplier<T> factory, Consumer<T> releaser) {
        return new ThreadResourcePool<>(factory, releaser);
    }

    /**
     * Acquires a resource handle from the pool.
     * <p>
     * This method provides a {@link Lease} which must be closed after use,
     * typically using a try-with-resources block.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * try (var lease = pool.acquire()) {
     * T resource = lease.get();
     * // use resource
     * }
     * }</pre>
     *
     * @return a lease containing the resource
     * @throws IllegalStateException if the current thread has already acquired
     * a resource from this pool and has not yet released it.
     */
    Lease<T> acquire();

    /**
     * Attempts to acquire a resource from the pool without blocking.
     * <p>
     * If a resource is available, this method returns a {@link Lease} providing
     * access to the resource. If no resources are available, it returns {@code null}.
     * <p>
     * The returned {@link Lease}, if not {@code null}, must be closed after use
     * to ensure proper resource management.
     *
     * @return a {@link Lease} containing the resource if one is available, or {@code null}
     *         if no resources are currently available
     */
    @Nullable Lease<T> tryAcquire();
}

/**
 * Implementation of the {@link ResourcePool.Lease} interface for managing the lifecycle of a resource.
 *
 * @param <T> the type of the resource being leased
 */
class LeaseImpl<T> implements ResourcePool.Lease<T> {
    final T resource;
    final Consumer<T> releaser;
    boolean leased = false;

    /**
     * Constructs a new {@code LeaseImpl} instance for managing the lifecycle of a resource.
     *
     * @param resource the resource being leased
     * @param releaser a {@link Consumer} to handle releasing the resource when the lease ends
     */
    LeaseImpl(T resource, Consumer<T> releaser) {
        this.resource = resource;
        this.releaser = releaser;
    }

    @Override
    public T get() {
        return resource;
    }

    @Override
    public void close() {
        if (!leased) {throw new IllegalStateException("resource not leased");}
        try {
            releaser.accept(resource);
        } catch (RuntimeException e) {
            SLB4J.logInternal(LogLevel.WARN, "Failed to release resource, exception ignored: {}", e.getMessage(), e);
        } finally {
            leased = false;
        }
    }

    LeaseImpl<T> acquire() {
        if (leased) {throw new IllegalStateException("resource already leased");}
        leased = true;
        return this;
    }
}

/**
 * Implementation of a thread-local resource pool that provides a unique resource per thread.
 * Each thread has exclusive access to its resource, which is managed via the {@link Lease} interface.
 *
 * @param <T> the type of resource managed by the pool
 */
final class ThreadResourcePool<T> implements ResourcePool<T> {
    private final ThreadLocal<LeaseImpl<T>> threadLocalLease;

    ThreadResourcePool(Supplier<T> factory, Consumer<T> releaser) {
        // We store the wrapper itself in the ThreadLocal
        this.threadLocalLease = ThreadLocal.withInitial(() -> new LeaseImpl<>(factory.get(), releaser));
    }

    @Override
    public Lease<T> acquire() {
        LeaseImpl<T> lease = threadLocalLease.get();
        if (lease.leased) {throw new IllegalStateException("resource already leased");}
        lease.leased = true;
        return lease;
    }

    @Override
    public @Nullable Lease<T> tryAcquire() {
        LeaseImpl<T> lease = threadLocalLease.get();

        if (lease.leased) {
            return null;
        } else {
            lease.leased = true;
            return lease;
        }
    }
}
