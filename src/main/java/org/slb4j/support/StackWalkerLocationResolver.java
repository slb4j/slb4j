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
import org.slb4j.LocationResolver;
import org.jspecify.annotations.Nullable;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.stream.Stream;

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
        return StackWalker.getInstance().walk(this::findStackFrame);
    }

    private @Nullable StackFrameLocation findStackFrame(Stream<StackFrame> stream) {
        StackFrame foundFrame = null;
        boolean foundInfra = false;
        boolean skippedInfra = false;

        StackFrame frame = null;
        java.util.Iterator<StackFrame> iterator = stream.iterator();

        // 1. Skip frames until we hit ANY logging infrastructure
        while (iterator.hasNext() && !isInfra((frame = iterator.next()).getClassName())) {
            frame = null; // not the one we look for
        }

        // 2. Skip EVERYTHING that is still logging infrastructure
        while (iterator.hasNext() && isInfra((frame = iterator.next()).getClassName())) {
            frame = null; // not the one we look for
        }

        // 3. The first non-infra frame is the user
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
