package org.slb4j.support;

import org.slb4j.Location;
import org.slb4j.LocationResolver;
import org.jspecify.annotations.Nullable;

import java.lang.StackWalker.StackFrame;
import java.util.List;

/**
 * A utility class responsible for determining the originating stack frame outside of
 * specified infrastructure packages.
 * <p>
 * This class is immutable and thread-safe.
 */
public final class StackWalkerLocationResolver implements LocationResolver {

    private final List<String> infraPackages;

    /**
     * Constructs a new {@code LocationResolver} instance with the specified list of
     * infrastructure package prefixes. The resolver will use this list to determine
     * which package names to treat as part of the logging infrastructure when analyzing
     * the call stack.
     *
     * @param infraPackages a list of package name prefixes representing the
     *                      infrastructure components to be excluded when resolving
     *                      the relevant stack frame
     */
    public StackWalkerLocationResolver(String... infraPackages) {
        this.infraPackages = List.of(infraPackages);
    }

    /**
     * Resolves the first stack frame in the call stack that does not belong to the
     * specified logging infrastructure packages.
     *
     * @return the first non-infrastructure-related stack frame, or {@code null} if no such
     *         frame exists in the stack trace.
     */
    public @Nullable Location resolve() {
        StackFrame frame = StackWalker.getInstance().walk(stream -> stream
                // 1. Skip frames until we hit ANY logging infrastructure
                .dropWhile(f -> !isInfra(f.getClassName()))
                // 2. Skip EVERYTHING that is still logging infrastructure
                .dropWhile(f -> isInfra(f.getClassName()))
                // 3. The first non-infra frame is the user
                .findFirst()
                .orElse(null)
        );

        return frame == null ? null : new StackFrameLocation(frame);
    }

    private boolean isInfra(String className) {
        for (String pkg : infraPackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    private static class StackFrameLocation implements Location {

        private final StackFrame frame;

        StackFrameLocation(StackFrame frame) {this.frame = frame;}

        @Override
        public @Nullable String getClassName() {
            return frame.getClassName();
        }

        @Override
        public @Nullable String getMethodName() {
            return frame.getMethodName();
        }

        @Override
        public int getLineNumber() {
            return frame.getLineNumber();
        }

        @Override
        public @Nullable String getFileName() {
            return frame.getFileName();
        }
    }
}
