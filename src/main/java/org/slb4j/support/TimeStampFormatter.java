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

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
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
public final class TimeStampFormatter {

    private static final int[] ZELLER_TO_CALENDAR = {7, 1, 2, 3, 4, 5, 6};

    private static final char[] DIGIT_TENS = {
            '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
            '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
            '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
            '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
            '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
            '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
            '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
            '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
            '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
            '9', '9', '9', '9', '9', '9', '9', '9', '9', '9',
    };

    private static final char[] DIGIT_ONES = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    };

    private final Part[] compiledParts;
    private final ZoneId zoneId;
    private final Locale locale;
    private final TimeZoneOffsetProvider offsetProvider;

    private TimeStampFormatter(Part[] parts, ZoneId zoneId, Locale locale) {
        this.compiledParts = parts;
        this.zoneId = zoneId;
        this.locale = locale;
        this.offsetProvider = new TimeZoneOffsetProvider(zoneId);
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
        // highly optimized timestamp formatters for default format
        switch (pattern) {
        case "yyyy-MM-dd HH:mm:ss.SSS"
                -> {return getISO8601StyleTimeStampFormatter(' ', '.', zoneId, locale);}
        case "yyyy-MM-ddTHH:mm:ss.SSS"
                -> {return getISO8601StyleTimeStampFormatter('T', '.', zoneId, locale);}
        case "yyyy-MM-dd HH:mm:ss,SSS"
                -> {return getISO8601StyleTimeStampFormatter(' ', ',', zoneId, locale);}
        case "yyyy-MM-dd'T'HH:mm:ss,SSS"
                -> {return getISO8601StyleTimeStampFormatter('T', ',', zoneId, locale);}
        }

        // custom formats
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
        return new TimeStampFormatter(parts.toArray(Part[]::new), zoneId, locale);
    }

    private static TimeStampFormatter getISO8601StyleTimeStampFormatter(char dateTimeSeparator, char millisSeparator, ZoneId zoneId, Locale locale) {
        return new TimeStampFormatter(new Part[]{(app, y, M, d, H, m, s, S) -> {
            int q = y / 100;
            app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
            q = y % 100;
            app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
            app.append('-');
            app.append(DIGIT_TENS[M]).append(DIGIT_ONES[M]);
            app.append('-');
            app.append(DIGIT_TENS[d]).append(DIGIT_ONES[d]);
            app.append(dateTimeSeparator);
            app.append(DIGIT_TENS[H]).append(DIGIT_ONES[H]);
            app.append(':');
            app.append(DIGIT_TENS[m]).append(DIGIT_ONES[m]);
            app.append(':');
            app.append(DIGIT_TENS[s]).append(DIGIT_ONES[s]);
            app.append(millisSeparator);
            int q1 = S / 100;
            app.append(DIGIT_ONES[q1]);
            q1 = S % 100;
            app.append(DIGIT_TENS[q1]).append(DIGIT_ONES[q1]);
        }}, zoneId, locale);
    }

    private static Part createLiteralPart(String literal) {
        return (app, y, M, d, H, m, s, S) -> app.append(literal);
    }

    /**
     * Appends a formatted representation of the given timestamp to the specified {@code Appendable}.
     * The method calculates the necessary date and time components based on the provided timestamp,
     * taking into account time zone offsets and day-light saving time adjustments. The formatted
     * result is constructed using the pre-configured {@code Part} elements of this formatter.
     *
     * @param timestamp the epoch timestamp in milliseconds, representing the moment to be formatted.
     *                  This value is interpreted based on the time zone and offset settings.
     * @param app       the {@code Appendable} to which the formatted output will be appended.
     *                  Common implementations include {@code StringBuilder}, {@code StringBuffer},
     *                  or {@code Writer}.
     * @throws IOException if an I/O error occurs while appending to the provided {@code Appendable}.
     */
    @SuppressWarnings("NumericCastThatLosesPrecision")
    public void appendTo(long timestamp, Appendable app) throws IOException {
        // Step 1: Get the offset for this specific moment (Required for DST)
        int offset = offsetProvider.getOffset(timestamp);

        long localSecond = Math.floorDiv(timestamp, 1000L) + offset;
        int millis = (int) Math.floorMod(timestamp, 1000L);

        // Step 2: Epoch Day Math
        long epochDay = Math.floorDiv(localSecond, 86400L);
        int secsOfDay = (int) Math.floorMod(localSecond, 86400L);

        // March-Epoch shift logic (Corrected)
        long marchDot = epochDay + 719468L;
        long era = (marchDot >= 0 ? marchDot : marchDot - 146096L) / 146097L;
        int doe = (int) (marchDot - era * 146097L);
        int yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        int y = (int) (yoe + era * 400);
        int doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        int mp = (5 * doy + 2) / 153;

        int d = doy - (153 * mp + 2) / 5 + 1;
        int m = mp + (mp < 10 ? 3 : -9);

        // Final Correction: If month is Jan/Feb, it belongs to the 'next' year
        // because the algorithm starts the year in March.
        if (m <= 2) y++;

        int hour = secsOfDay / 3600;
        int minute = (secsOfDay % 3600) / 60;
        int second = secsOfDay % 60;

        for (Part part : compiledParts) {
            part.append(app, y, m, d, hour, minute, second, millis);
        }
    }

    private static int getDayOfWeek(int y, int m, int d) {
        if (m < 3) {
            m += 12;
            y--;
        }
        int k = y % 100;
        int j = y / 100;
        // Zeller's congruence
        int h = (d + 13 * (m + 1) / 5 + k + k / 4 + j / 4 + 5 * j) % 7;
        // Zeller returns 0=Sat, 1=Sun, ..., 6=Fri
        // Calendar.SUNDAY = 1, MONDAY = 2, ..., SATURDAY = 7
        return ZELLER_TO_CALENDAR[h];
    }

    private static String[] getMonthNames(Locale locale, boolean full) {
        java.text.DateFormatSymbols symbols = java.text.DateFormatSymbols.getInstance(locale);
        String[] months = full ? symbols.getMonths() : symbols.getShortMonths();
        // DateFormatSymbols.getMonths() returns an array of 13 strings, the last one is empty.
        // We only need the first 12.
        if (months.length > 12) {
            String[] result = new String[12];
            System.arraycopy(months, 0, result, 0, 12);
            return result;
        }
        return months;
    }

    private static String[] getDayNames(Locale locale, boolean full) {
        java.text.DateFormatSymbols symbols = java.text.DateFormatSymbols.getInstance(locale);
        return full ? symbols.getWeekdays() : symbols.getShortWeekdays();
    }

    private static String[] getAmPmStrings(Locale locale) {
        java.text.DateFormatSymbols symbols = java.text.DateFormatSymbols.getInstance(locale);
        return symbols.getAmPmStrings();
    }

    private static Part createPart(char c, int count, Locale locale) {
        return switch (c) {
            case 'y' ->
                switch (count) {
                     case 2 -> (app, y, M, d, H, m, s, S) -> appendInt2(app, y % 100);
                     case 4 -> (app, y, M, d, H, m, s, S) -> appendInt4(app, y);
                     default -> (app, y, M, d, H, m, s, S) -> app.append(Integer.toString(y));
                };
            case 'M' ->
                switch (count) {
                    case 0, 1 -> (app, y, M, d, H, m, s, S) -> appendInt(app, count, M);
                    case 2 -> (app, y, M, d, H, m, s, S) -> appendInt2(app, M);
                    default -> {
                        String[] names = getMonthNames(locale, count >= 4);
                        yield (app, y, M, d, H, m, s, S) -> app.append(names[M - 1]);
                    }
                };
            case 'd' -> switch (count) {
                case 2 -> (app, y, M, d, H, m, s, S) -> appendInt2(app, d);
                default -> (app, y, M, d, H, m, s, S) -> appendInt(app, count, d);
            };
            case 'E' -> {
                String[] names = getDayNames(locale, count >= 4);
                yield (app, y, M, d, H, m, s, S) -> app.append(names[getDayOfWeek(y, M, d)]);
            }
            case 'h' ->
                switch (count) {
                    case 2 -> (app, y, M, d, H, m, s, S) -> appendInt2(app, getHour12(H));
                    default -> (app, y, M, d, H, m, s, S) -> appendInt(app, count, getHour12(H));
                };
            case 'H' ->
                    switch (count) {
                        case 2 -> (app, y, M, d, H, m, s, S) -> appendInt2(app, H);
                        default -> (app, y, M, d, H, m, s, S) -> appendInt(app, count, H);
                    };
            case 'm' ->
                    switch (count) {
                        case 2 -> (app, y, M, d, H, m, s, S) -> appendInt2(app, m);
                        default -> (app, y, M, d, H, m, s, S) -> appendInt(app, count, m);
                    };
            case 's' ->
                    switch (count) {
                        case 2 -> (app, y, M, d, H, m, s, S) -> appendInt2(app, s);
                        default -> (app, y, M, d, H, m, s, S) -> appendInt(app, count, s);
                    };
            case 'S' ->
                    switch (count) {
                        case 2 -> (app, y, M, d, H, m, s, S) -> appendInt2(app, S);
                        case 3 -> (app, y, M, d, H, m, s, S) -> appendInt3(app, S);
                        default -> (app, y, M, d, H, m, s, S) -> appendInt(app, count, S);
                    };
            case 'a' -> {
                String[] ampm = getAmPmStrings(locale);
                yield (app, y, M, d, H, m, s, S) -> app.append(H < 12 ? ampm[0] : ampm[1]);
            }
            default -> switch (count) {
                case 0 -> (app, y, M, d, H, m, s, S) -> {};
                case 1 -> (app, y, M, d, H, m, s, S) -> app.append(c);
                default -> {
                    String literal = String.valueOf(c).repeat(count);
                    yield (app, y, M, d, H, m, s, S) -> app.append(literal);
                }
            };
        };
    }

    private static int getHour12(int H) {
        int hour12 = H % 12;
        return hour12 == 0 ? 12 : hour12;
    }

    @FunctionalInterface
    private interface Part {
        void append(Appendable app, int y, int M, int d, int H, int m, int s, int S) throws IOException;
    }

    private static void appendInt(Appendable app, int digits, int val) throws IOException {
        switch (digits) {
            case 2 -> appendInt2(app, val);
            case 3 -> appendInt3(app, val);
            case 4 -> appendInt4(app, val);
            default -> app.append(Integer.toString(val));
        }
    }

    private static void appendInt4(Appendable app, int val) throws IOException {
        int q = val / 100;
        app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
        q = val % 100;
        app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
    }

    private static void appendInt3(Appendable app, int val) throws IOException {
        int q = val / 100;
        app.append(DIGIT_ONES[q]);
        q = val % 100;
        app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
    }

    private static Appendable appendInt2(Appendable app, int val) throws IOException {
        return app.append(DIGIT_TENS[val]).append(DIGIT_ONES[val]);
    }

    /**
     * Converts the given timestamp to its corresponding string representation.
     * The method formats the timestamp using a builder-like approach, relying on
     * internally configured components such as pre-defined formatting rules and
     * time zone or offset settings.
     * <p>
     * This method is intended for debugging purposes only (it is not garbage-free,
     * i.e., when used for logging might create a bottleneck).
     *
     * @param timestamp the epoch timestamp in milliseconds that is to be formatted
     *                  into a string representation. It is interpreted based on
     *                  the configured time zone and offsets.
     * @return the string representation of the provided timestamp, formatted
     *         according to the internal configuration of this formatter.
     * @throws UncheckedIOException if an I/O error occurs while constructing
     *         the formatted string.
     */
    public String toString(long timestamp) {
        try {
            StringBuilder sb = new StringBuilder(32);
            appendTo(timestamp, sb);
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Internal error", e);
        }
    }
}
