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

import java.lang.StackWalker.StackFrame;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A utility class responsible for determining the originating stack frame outside of
 * the specified infrastructure packages.
 * <p>
 * This class is immutable and thread-safe.
 */
public final class StackWalkerLocationResolver implements LocationResolver {

    private static final StackWalker STACK_WALKER = StackWalker.getInstance();
    private final String loggerClassName;
    private final String infraPackage;

    /**
     * Constructs a new {@code LocationResolver} instance with the specified list of
     * infrastructure package prefixes. The resolver will use this list to determine
     * which package names to treat as part of the logging infrastructure when analyzing
     * the call stack.
     *
     * @param loggerClass   the class of the logger
     * @param infraPackage  a list of package name prefixes representing the
     *                      infrastructure components to be excluded when resolving
     *                      the relevant stack frame
     */
    public StackWalkerLocationResolver(Class<?> loggerClass, String infraPackage) {
        this.loggerClassName = loggerClass.getName();
        this.infraPackage = infraPackage;
    }

    /**
     * Resolves the first stack frame in the call stack that does not belong to the
     * specified logging infrastructure packages.
     *
     * @return the first non-infrastructure-related stack frame, or {@code null} if no such
     *         frame exists in the stack trace.
     */
    public Location resolve() {
        return STACK_WALKER.walk(this::findStackFrame);
    }

    private StackFrameLocation findStackFrame(Stream<StackFrame> stream) {
        try {
            java.util.Iterator<StackFrame> iterator = stream.iterator();

            // 1. Skip the StackWalkerLocationResolver.resolve() frame
            iterator.next();

            // 2. Skip frames until we hit the logger instance
            while (!loggerClassName.equals(iterator.next().getClassName())) {
                // nothing to do
            }

            // 3. Skip all frames that still belong to the logging infrastructure
            StackFrame frame;
            while ((frame = iterator.next()).getClassName().startsWith(infraPackage)) {
                // nothing to do
            }

            // 4. The first non-infra frame is the logging call site
            return new StackFrameLocation(frame);
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("Internal error - no stack frame found", e);
        }
    }

    private record StackFrameLocation(StackFrame frame) implements Location {
        @Override
        public String getClassName() {
            return frame.getClassName();
        }

        @Override
        public String getMethodName() {
            return frame.getMethodName();
        }

        @Override
        public int getLineNumber() {
            return frame.getLineNumber();
        }

        @Override
        public String getFileName() {
            return Objects.requireNonNullElse(frame.getFileName(), "<unknown>");
        }
    }
}
