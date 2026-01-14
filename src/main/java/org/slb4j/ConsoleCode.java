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

import org.slb4j.support.AnsiCode;

/**
 * A record representing console code sequences defining start and end formatting.
 *
 * @param start the start sequence for the console code
 * @param end the end sequence for the console code
 */
public record ConsoleCode(String start, String end) {

    private static final ConsoleCode EMPTY = new ConsoleCode("", "");

    /**
     * Creates a new instance of {@code ConsoleCode} with the specified start and end sequences.
     *
     * @param start the start sequence for the console code
     * @param end the end sequence for the console code
     * @return a new {@code ConsoleCode} instance with the provided start and end sequences
     */
    public static ConsoleCode of(String start, String end) {
        return new ConsoleCode(start, end);
    }

    /**
     * Creates a {@link ConsoleCode} instance with the specified ANSI start sequence
     * and a predefined reset sequence as the end sequence.
     *
     * @param start the ANSI start sequence for the console code
     * @return a {@link ConsoleCode} instance using the specified start sequence and
     *         the ANSI reset sequence as the end sequence
     */
    public static ConsoleCode ofAnsi(String start) {
        return new ConsoleCode(start, AnsiCode.reset());
    }

    /**
     * Returns an empty ConsoleCode instance with no start or end formatting.
     *
     * @return a ConsoleCode instance representing an empty formatting sequence.
     */
    public static ConsoleCode empty() {
        return EMPTY;
    }
}
