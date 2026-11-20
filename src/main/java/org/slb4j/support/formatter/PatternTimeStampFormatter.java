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
package org.slb4j.support.formatter;

import org.slb4j.support.TimeStampFormatter;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Locale;

/**
 * A utility class for formatting timestamps based on custom patterns.
 * <p>
 * This highly optimized implementation works directly on timestamp
 * values in milliseconds passed as {@code long} values. This avoids
 * creating temporary objects (i.e. {@link java.time.Instant}) and
 * recues GC load.
 * <p>
 * The class is thread-safe.
 */
public final class PatternTimeStampFormatter extends AbstractTimeStampFormatter {

    private final Part[] compiledParts;

    private PatternTimeStampFormatter(Part[] parts, ZoneId zoneId) {
        super(zoneId);
        this.compiledParts = parts;
    }

    /**
     * Parses the given pattern to create a {@code TimeStampFormatter} configured with the system default time zone.
     * The pattern specifies how a timestamp should be formatted or parsed, using character sequences
     * to represent specific date or time components.
     *
     * @param pattern the pattern describing the date and time format, where special characters
     *                represent specific timestamp components or literal text.
     * @return a {@code TimeStampFormatter} instance configured according to the provided pattern
     *         and the system default time zone.
     * @throws IllegalArgumentException if the pattern is invalid or contains unsupported components.
     */
    public static TimeStampFormatter parse(String pattern) {
        return parse(pattern, ZoneId.systemDefault(), Locale.getDefault());
    }

    /**
     * Parses the given pattern and time zone to create a {@code TimeStampFormatter}.
     * The pattern describes how a timestamp should be formatted or parsed using
     * specific characters to represent date or time components, and literal sequences
     * for fixed text.
     *
     * @param pattern the pattern describing the date and time format, where special
     *                characters represent timestamp components and single quotes
     *                can be used to define literal text.
     * @param zoneId  the time zone to be used in conjunction with the parsed pattern.
     * @return a {@code TimeStampFormatter} instance configured according to the
     *         provided pattern and time zone.
     * @throws IllegalArgumentException if the pattern is invalid or contains unsupported components.
     */
    public static TimeStampFormatter parse(String pattern, ZoneId zoneId) {
        return parse(pattern, zoneId, Locale.getDefault());
    }

    /**
     * Parses the given pattern, time zone and locale to create a {@code TimeStampFormatter}.
     * The pattern describes how a timestamp should be formatted or parsed using
     * specific characters to represent date or time components, and literal sequences
     * for fixed text.
     *
     * @param pattern the pattern describing the date and time format, where special
     *                characters represent timestamp components and single quotes
     *                can be used to define literal text.
     * @param zoneId  the time zone to be used in conjunction with the parsed pattern.
     * @param locale  the locale to be used for formatting month and day names.
     * @return a {@code TimeStampFormatter} instance configured according to the
     *         provided pattern, time zone and locale.
     * @throws IllegalArgumentException if the pattern is invalid or contains unsupported components.
     */
    public static TimeStampFormatter parse(String pattern, ZoneId zoneId, Locale locale) {
        // highly optimized timestamp formatters for the most use formats
        return switch (pattern) {
            case "yyyy-MM-ddTHH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss.SSS" -> new ISO8601TimeStampFormatter('T', '.', false, zoneId);
            case "yyyy-MM-dd HH:mm:ss.SSS" -> new ISO8601TimeStampFormatter(' ', '.', false, zoneId);
            case "yyyy-MM-dd HH:mm:ss,SSS" -> new ISO8601TimeStampFormatter(' ', ',', false, zoneId);
            case "yyyy-MM-ddTHH:mm:ss,SSS", "yyyy-MM-dd'T'HH:mm:ss,SSS" -> new ISO8601TimeStampFormatter('T', ',', false, zoneId);
            case "yyyy-MM-ddTHH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ss.SSSX" -> TimeStampFormatter.ISO8601_FORMATTER;
            case "yyyy-MM-dd HH:mm:ss.SSSX" -> new ISO8601TimeStampFormatter(' ', '.', true, zoneId);
            case "yyyy-MM-dd HH:mm:ss,SSSX" -> TimeStampFormatter.DEFAULT_FORMATTER;
            case "yyyy-MM-ddTHH:mm:ss,SSSX", "yyyy-MM-dd'T'HH:mm:ss,SSSX" -> new ISO8601TimeStampFormatter('T', ',', true, zoneId);
            case "HH:mm:ss.SSS" -> new TimeOnlyTimeStampFormatter('.', zoneId);
            case "HH:mm:ss,SSS" -> new TimeOnlyTimeStampFormatter(',', zoneId);
            default -> parseCustomFormat(pattern, zoneId, locale);
        };
    }

    /**
     * Parses a custom format pattern along with a specified time zone and locale to create
     * a {@code TimeStampFormatter}. The pattern defines how a timestamp should be formatted
     * or parsed using specific characters for date and time components, and literal sequences
     * for fixed text.
     *
     * @param pattern the pattern describing the date and time format, where special
     *                characters represent specific components of the timestamp
     *                and single quotes define literal text.
     * @param zoneId  the time zone to be used for formatting or parsing timestamps.
     * @param locale  the locale to be used for formatting locale-specific components
     *                such as month or day names.
     * @return a {@code TimeStampFormatter} instance configured according to the specified
     *         pattern, time zone, and locale.
     * @throws IllegalArgumentException if the pattern is invalid or contains unsupported components.
     */
    private static TimeStampFormatter parseCustomFormat(String pattern, ZoneId zoneId, Locale locale) {
        java.util.List<Part> parts = new java.util.ArrayList<>();
        int i = 0;
        boolean inQuote = false;
        StringBuilder currentLiteral = new StringBuilder();

        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') {
                    if (inQuote) {
                        currentLiteral.append('\'');
                    } else {
                        // Double single quote outside of quotes means a single quote literal
                        parts.add(createLiteralPart("'"));
                    }
                    i += 2;
                    continue;
                }
                inQuote = !inQuote;
                if (!inQuote && !currentLiteral.isEmpty()) {
                    parts.add(createLiteralPart(currentLiteral.toString()));
                    currentLiteral.setLength(0);
                }
                i++;
                continue;
            }

            if (inQuote) {
                currentLiteral.append(c);
            } else {
                int count = 1;
                while (i + 1 < pattern.length() && pattern.charAt(i + 1) == c) {
                    i++;
                    count++;
                }
                parts.add(createPart(c, count, locale));
            }
            i++;
        }
        return new PatternTimeStampFormatter(parts.toArray(Part[]::new), zoneId);
    }

    /**
     * Creates a literal part for a template or formatter.
     *
     * @param literal the string literal to be appended to the output
     * @return a Part instance that appends the specified literal to the output
     */
    private static Part createLiteralPart(String literal) {
        return switch (literal.length()) {
            case 0 -> new EmptyPart();
            case 1 -> new LiteralCharPart(literal.charAt(0));
            default -> new LiteralStringPart(literal);
        };
    }

    /**
     * Represents a part that does not create any output.
     */
    private static final class EmptyPart implements Part {
        @Override
        public void append(Appendable app, int y, int M, int d, int H, int m, int s, int S, int offsetSeconds) throws IOException {
            // nothing to do
        }
    }

    /**
     * Represents a literal part of a formatted output, given as a String.
     *
     * @param literal the fixed sequence of characters that this part represents
     */
    private static final record LiteralStringPart(String literal) implements Part {
        @Override
        public void append(Appendable app, int y, int M, int d, int H, int m, int s, int S, int offsetSeconds) throws IOException {
            app.append(literal);
        }
    }

    /**
     * Represents a literal part of a formatted output, given as a single character.
     *
     * @param c the character that this part represents
     */
    private static final record LiteralCharPart(char c) implements Part {
        @Override
        public void append(Appendable app, int y, int M, int d, int H, int m, int s, int S, int offsetSeconds) throws IOException {
            app.append(c);
        }
    }

    @Override
    protected void appendTo(Appendable app, int y, int m, int d, int hour, int minute, int second, int millis, int offsetSeconds) throws IOException {
        for (int i = 0; i < compiledParts.length; i++) {
            compiledParts[i].append(app, y, m, d, hour, minute, second, millis, offsetSeconds);
        }
    }

    /**
     * Creates a formatting part for the specified date-time pattern character and repeat count.
     * The returned part appends the corresponding formatted value, localized text, time-zone
     * offset, literal character sequence, or no output depending on the pattern character.
     *
     * @param c the pattern character identifying the date-time field or literal to format
     * @param count the number of consecutive occurrences of the pattern character
     * @param locale the locale used to resolve localized month names, day names, and AM/PM strings
     * @return a formatting part that appends the value represented by the specified pattern character
     */
    private static Part createPart(char c, int count, Locale locale) {
        return switch (c) {
            case 'y' ->
                switch (count) {
                     case 2 -> (app, y, M, d, H, m, s, S, O) -> appendInt2(app, y % 100);
                     case 4 -> (app, y, M, d, H, m, s, S, O) -> appendInt4(app, y);
                     default -> (app, y, M, d, H, m, s, S, O) -> app.append(Integer.toString(y));
                };
            case 'M' ->
                switch (count) {
                    case 0, 1 -> (app, y, M, d, H, m, s, S, O) -> appendInt(app, count, M);
                    case 2 -> (app, y, M, d, H, m, s, S, O) -> appendInt2(app, M);
                    default -> {
                        String[] names = getMonthNames(locale, count >= 4);
                        yield (app, y, M, d, H, m, s, S, O) -> app.append(names[M - 1]);
                    }
                };
            case 'd' -> switch (count) {
                case 2 -> (app, y, M, d, H, m, s, S, O) -> appendInt2(app, d);
                default -> (app, y, M, d, H, m, s, S, O) -> appendInt(app, count, d);
            };
            case 'E' -> {
                String[] names = getDayNames(locale, count >= 4);
                yield (app, y, M, d, H, m, s, S, O) -> app.append(names[getDayOfWeek(y, M, d)]);
            }
            case 'h' ->
                switch (count) {
                    case 2 -> (app, y, M, d, H, m, s, S, O) -> appendInt2(app, getHour12(H));
                    default -> (app, y, M, d, H, m, s, S, O) -> appendInt(app, count, getHour12(H));
                };
            case 'H' ->
                    switch (count) {
                        case 2 -> (app, y, M, d, H, m, s, S, O) -> appendInt2(app, H);
                        default -> (app, y, M, d, H, m, s, S, O) -> appendInt(app, count, H);
                    };
            case 'm' ->
                    switch (count) {
                        case 2 -> (app, y, M, d, H, m, s, S, O) -> appendInt2(app, m);
                        default -> (app, y, M, d, H, m, s, S, O) -> appendInt(app, count, m);
                    };
            case 's' ->
                    switch (count) {
                        case 2 -> (app, y, M, d, H, m, s, S, O) -> appendInt2(app, s);
                        default -> (app, y, M, d, H, m, s, S, O) -> appendInt(app, count, s);
                    };
            case 'S' ->
                    switch (count) {
                        case 2 -> (app, y, M, d, H, m, s, S, O) -> appendInt2(app, S);
                        case 3 -> (app, y, M, d, H, m, s, S, O) -> appendInt3(app, S);
                        case 4, 5, 6, 7, 8, 9 -> {
                            String zeros = "0".repeat(count - 3);
                            yield (app, y, M, d, H, m, s, S, O) -> {
                                appendInt3(app, S);
                                app.append(zeros);
                            };
                        }
                        default -> (app, y, M, d, H, m, s, S, O) -> appendInt(app, count, S);
                    };
            case 'a' -> {
                String[] ampm = getAmPmStrings(locale);
                yield (app, y, M, d, H, m, s, S, O) -> app.append(H < 12 ? ampm[0] : ampm[1]);
            }
            case 'X' -> createOffsetPart(count, true);
            case 'x' -> createOffsetPart(count, false);
            default -> switch (count) {
                case 0 -> (app, y, M, d, H, m, s, S, O) -> {};
                case 1 -> (app, y, M, d, H, m, s, S, O) -> app.append(c);
                default -> {
                    String literal = String.valueOf(c).repeat(count);
                    yield (app, y, M, d, H, m, s, S, O) -> app.append(literal);
                }
            };
        };
    }

    /**
     * Creates a timestamp formatting part that appends the time zone offset using
     * the specified offset pattern width.
     *
     * @param count the number of offset pattern letters, from 1 to 5, determining
     *              whether minutes and seconds are included and whether separators
     *              are used.
     * @param zeroAsZ whether a zero offset should be written as {@code Z}; when
     *                {@code false}, zero is written as a numeric offset.
     * @return a {@code Part} that appends the formatted time zone offset to the
     *         output.
     * @throws IllegalArgumentException if {@code count} is greater than 5.
     */
    private static Part createOffsetPart(int count, boolean zeroAsZ) {
        if (zeroAsZ) {
            return switch (count) {
                case 1 -> (app, y, M, d, H, m, s, S, O) -> appendIsoOffset(app, O, 1);
                case 2 -> (app, y, M, d, H, m, s, S, O) -> appendIsoOffset(app, O, 2);
                case 3 -> (app, y, M, d, H, m, s, S, O) -> appendIsoOffset(app, O, 3);
                case 4 -> (app, y, M, d, H, m, s, S, O) -> appendIsoOffset(app, O, 4);
                case 5 -> (app, y, M, d, H, m, s, S, O) -> appendIsoOffset(app, O, 5);
                default -> throw new IllegalArgumentException("Too many pattern letters: X");
            };
        }
        return switch (count) {
            case 1 -> (app, y, M, d, H, m, s, S, O) -> appendNumericOffset(app, O, 1);
            case 2 -> (app, y, M, d, H, m, s, S, O) -> appendNumericOffset(app, O, 2);
            case 3 -> (app, y, M, d, H, m, s, S, O) -> appendNumericOffset(app, O, 3);
            case 4 -> (app, y, M, d, H, m, s, S, O) -> appendNumericOffset(app, O, 4);
            case 5 -> (app, y, M, d, H, m, s, S, O) -> appendNumericOffset(app, O, 5);
            default -> throw new IllegalArgumentException("Too many pattern letters: x");
        };
    }

    /**
     * Appends an ISO-8601 time zone offset to the specified output, writing {@code Z}
     * for a zero offset and a numeric offset otherwise.
     *
     * @param app the destination to which the offset is appended
     * @param offsetSeconds the time zone offset from UTC, in seconds
     * @param count the offset pattern width controlling the numeric offset format
     * @throws IOException if the destination throws an exception while appending
     */
    private static void appendIsoOffset(Appendable app, int offsetSeconds, int count) throws IOException {
        if (offsetSeconds == 0) {
            app.append('Z');
            return;
        }
        appendNumericOffset(app, offsetSeconds, count);
    }

    /**
     * Appends a numeric time zone offset to the specified output.
     * The offset is written with a leading sign, two-digit hours, and optional
     * minutes and seconds according to the offset pattern width.
     *
     * @param app the destination to which the numeric offset is appended
     * @param offsetSeconds the time zone offset from UTC, in seconds
     * @param count the offset pattern width controlling whether minutes and
     *              seconds are included and whether colon separators are used
     * @throws IOException if the destination throws an exception while appending
     */
    private static void appendNumericOffset(Appendable app, int offsetSeconds, int count) throws IOException {
        int absoluteSeconds = Math.abs(offsetSeconds);
        int minutes = absoluteSeconds / 60 % 60;
        app.append(offsetSeconds < 0 ? '-' : '+');
        appendInt2(app, absoluteSeconds / 3600);
        if (count > 1 || minutes != 0) {
            if (count == 3 || count == 5) {
                app.append(':');
            }
            appendInt2(app, minutes);
            int seconds = absoluteSeconds % 60;
            if (count >= 4 && seconds != 0) {
                if (count == 5) {
                    app.append(':');
                }
                appendInt2(app, seconds);
            }
        }
    }

}
