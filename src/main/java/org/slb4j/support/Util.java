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

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Supplier;

/**
 * Utility class that provides helper methods for common programming tasks.
 */
public final class Util {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    /** An immutable, pre-initialized, empty array of {@code String}. */
    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    private Util() {
        // utility class, no instances
    }

    /**
     * Provides access to the standard output stream.
     *
     * @return the standard output stream
     */
    public static PrintStream out() {
        return System.out;
    }

    /**
     * Provides access to the error output stream.
     *
     * @return the standard output stream
     */
    public static PrintStream err() {
        return System.err;
    }

    /**
     * Wraps a supplier of a string to defer execution of the string creation
     * until its actual usage. This can be useful in scenarios where the string
     * construction is expensive and may not always be needed.
     *
     * @param supplier a supplier that provides the string lazily when requested
     * @return an object that defers the evaluation of the supplier until its string representation is required
     */
    public static Supplier<String> cachingStringSupplier(Supplier<String> supplier) {
        return supplier instanceof CachingStringSupplier cs ? cs : new CachingStringSupplier(supplier);
    }

    /**
     * Appends the stack trace of a throwable to a StringBuilder.
     * @param app the StringBuilder to append to
     * @param t the throwable
     * @throws IOException if an I/O error occurs
     */
    public static void appendStackTrace(Appendable app, Throwable t) throws IOException {
        app.append(String.valueOf(t)).append(LINE_SEPARATOR);
        for (StackTraceElement element : t.getStackTrace()) {
            app.append("\tat ").append(String.valueOf(element)).append(LINE_SEPARATOR);
        }
        for (Throwable suppressed : t.getSuppressed()) {
            appendStackTraceEnclosed(app, suppressed, t.getStackTrace(), "Suppressed: ", "\t");
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            appendStackTraceEnclosed(app, cause, t.getStackTrace(), "Caused by: ", "");
        }
    }

    private static void appendStackTraceEnclosed(Appendable app, Throwable t, StackTraceElement[] enclosingTrace, CharSequence caption, CharSequence indent) throws IOException {
        StackTraceElement[] trace = t.getStackTrace();
        int m = trace.length - 1;
        int n = enclosingTrace.length - 1;
        while (m >= 0 && n >= 0 && trace[m].equals(enclosingTrace[n])) {
            m--;
            n--;
        }
        int framesInCommon = trace.length - 1 - m;

        app.append(indent).append(caption).append(String.valueOf(t)).append(LINE_SEPARATOR);
        for (int i = 0; i <= m; i++) {
            app.append(indent).append("\tat ").append(String.valueOf(trace[i])).append(LINE_SEPARATOR);
        }
        if (framesInCommon != 0) {
            app.append(indent).append("\t... ").append(String.valueOf(framesInCommon)).append(" more").append(LINE_SEPARATOR);
        }

        for (Throwable suppressed : t.getSuppressed()) {
            appendStackTraceEnclosed(app, suppressed, trace, "Suppressed: ", indent + "\t");
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            appendStackTraceEnclosed(app, cause, trace, "Caused by: ", indent);
        }
    }

    /**
     * Finds the first occurrence of the specified character within the given {@link CharSequence}.
     *
     * @param cs the character sequence to search, must not be null
     * @param c the character to find
     * @return the index of the first occurrence of the character, or -1 if the character is not found
     */
    public static int indexOf(CharSequence cs, char c) {
        return indexOf(cs, c, 0);
    }

    /**
     * Finds the first occurrence of the specified character within the given {@link CharSequence},
     * starting the search from the specified index.
     *
     * @param cs the character sequence to search; must not be null
     * @param c the character to find
     * @param start the index to start the search from; must be non-negative and less than the length of the character sequence
     * @return the index of the first occurrence of the character, or -1 if the character is not found
     */
    public static int indexOf(CharSequence cs, char c, int start) {
        for (int i = start; i < cs.length(); i++) {
            if (cs.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Converts a {@link java.nio.file.Path} to a normalized string representation using forward slashes
     * as separators, regardless of the operating system.
     *
     * @param path the path to normalize
     * @return the normalized path string with forward slashes
     */
    public static String pathToNormalizedString(java.nio.file.Path path) {
        java.nio.file.Path p = path.normalize();  // preserves relative paths
        StringBuilder sb = new StringBuilder();

        java.nio.file.Path root = p.getRoot();
        if (root != null) {
            sb.append(root.toString().replace('\\', '/'));
        }

        for (java.nio.file.Path part : p) {
            if (sb.length() != 0 && sb.charAt(sb.length() - 1) != '/')
                sb.append('/');
            sb.append(part.toString());
        }

        return sb.toString();
    }

    private static final class CachingStringSupplier implements Supplier<String> {
        private final Supplier<String> supplier;
        private @Nullable String s;

        CachingStringSupplier(Supplier<String> supplier) {
            this.supplier = supplier;
            s = null;
        }

        @Override
        public String get() {
            return s != null ? s : (s = supplier.get());
        }

        @Override
        public String toString() {
            return get();
        }
    }

    /**
     * Checks if the specified class is available on the classpath.
     *
     * @param className the fully qualified name of the class to check (e.g., "java.util.List")
     * @return {@code true} if the class is found on the classpath, {@code false} otherwise
     */
    public static boolean isClassOnClasspath(String className) {
        String classResource = className.replace('.', '/') + ".class";
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader.getResource(classResource) != null;
    }
}
