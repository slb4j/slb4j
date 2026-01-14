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

/**
 * Support for ANSI escape codes for setting text attributes on ANSI-supporting consoles.
 */
public final class AnsiCode {

    /**
     * Marker: start of escape sequence.
     */
    private static final String ESC_START = "\033[";
    /**
     * Marker: end of escape sequence.
     */
    private static final String ESC_END = "m";

    /**
     * ESC: reset.
     */
    private static final char RESET = 0;
    /**
     * ESC: enable BOLD output.
     */
    private static final char BOLD_ON = 1;
    /**
     * ESC: disable BOLD output.
     */
    private static final char BOLD_OFF = 22;
    /**
     * ESC: enable REVERSE video output.
     */
    private static final char REVERSE_VIDEO_ON = 7;
    /**
     * ESC: disable REVERSE video output.
     */
    private static final char REVERSE_VIDEO_OFF = 27;
    /**
     * ESC: enable ITALIC output.
     */
    private static final char ITALIC_ON = 3;
    /**
     * ESC: disable ITALIC output.
     */
    private static final char ITALIC_OFF = 23;
    /**
     * ESC: enable UNDERLINE output.
     */
    private static final char UNDERLINE_ON = 4;
    /**
     * ESC: disable UNDERLINE output.
     */
    private static final char UNDERLINE_OFF = 24;
    /**
     * ESC: enable STRIKETHROUGH output.
     */
    private static final char STRIKE_THROUGH_ON = 9;
    /**
     * ESC: disable STRIKETHROUGH output.
     */
    private static final char STRIKE_THROUGH_OFF = 29;
    /**
     * ESC: start a color sequence.
     */
    private static final char COLOR = 38;
    /**
     * ESC: start a background color sequence.
     */
    private static final char BACKGROUND_COLOR = 48;

    private AnsiCode() { /* utility class */ }

    /**
     * Create an escape sequence.
     *
     * @param args the sequence arguments
     * @return escape sequence as a String
     */
    public static String esc(int... args) {
        StringBuilder out = new StringBuilder();
        out.append(ESC_START);
        String delimiter = "";
        for (int arg : args) {
            out.append(delimiter).append(arg);
            delimiter = ";";
        }
        out.append(ESC_END);
        return out.toString();
    }

    /**
     * Set text color.
     *
     * @param r the red component value
     * @param g the green component value
     * @param b the blue component value
     * @return the ESC string
     */
    public static String fg(int r, int g, int b) {
        return esc(COLOR, 2, r, g, b);
    }

    /**
     * Set background color.
     *
     * @param r the red component value
     * @param g the green component value
     * @param b the blue component value
     * @return the ESC string
     */
    public static String bg(int r, int g, int b) {
        return esc(BACKGROUND_COLOR, 2, r, g, b);
    }

    /**
     * Reset.
     *
     * @return the ESC string
     */
    public static String reset() {
        return esc(RESET);
    }

    /**
     * Set underline output.
     *
     * @param on {@code true} enables, {@code false} disables
     * @return the ESC string
     */
    public static String underline(boolean on) {
        return esc(on ? UNDERLINE_ON : UNDERLINE_OFF);
    }

    /**
     * Set reverse video output.
     *
     * @param on {@code true} enables, {@code false} disables
     * @return the ESC string
     */
    public static String reverse(boolean on) {
        return esc(on ? REVERSE_VIDEO_ON : REVERSE_VIDEO_OFF);
    }

    /**
     * Set strike-through output.
     *
     * @param on {@code true} enables, {@code false} disables
     * @return the ESC string
     */
    public static String strikeThrough(boolean on) {
        return esc(on ? STRIKE_THROUGH_ON : STRIKE_THROUGH_OFF);
    }

    /**
     * Set italic output.
     *
     * @param on {@code true} enables, {@code false} disables
     * @return the ESC string
     */
    public static String italic(boolean on) {
        return esc(on ? ITALIC_ON : ITALIC_OFF);
    }

    /**
     * Set bold output.
     *
     * @param on {@code true} enables, {@code false} disables
     * @return the ESC string
     */
    public static String bold(boolean on) {
        return esc(on ? BOLD_ON : BOLD_OFF);
    }
}
