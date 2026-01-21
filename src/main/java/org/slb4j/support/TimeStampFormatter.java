package org.slb4j.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;

public final class TimeStampFormatter {

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
    } ;

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
    } ;

    private final Part[] compiledParts;
    private final ZoneId zoneId;
    private final TimeZoneOffsetProvider offsetProvider;

    private TimeStampFormatter(Part[] parts, ZoneId zoneId) {
        this.compiledParts = parts;
        this.zoneId = zoneId;
        this.offsetProvider = new TimeZoneOffsetProvider(zoneId);
    }

    public static TimeStampFormatter parse(String pattern) {
        return parse(pattern, ZoneId.systemDefault());
    }

    public static TimeStampFormatter parse(String pattern, ZoneId zoneId) {
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
                i++;
            } else {
                int count = 1;
                while (i + 1 < pattern.length() && pattern.charAt(i + 1) == c) {
                    i++;
                    count++;
                }
                parts.add(createPart(c, count));
                i++;
            }
        }
        return new TimeStampFormatter(parts.toArray(Part[]::new), zoneId);
    }

    private static Part createLiteralPart(String literal) {
        return (app, y, M, d, H, m, s, S) -> app.append(literal);
    }

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

    @FunctionalInterface
    private interface Part {
        void append(Appendable app, int y, int M, int d, int H, int m, int s, int S) throws IOException;
    }

    private static Part createPart(char c, int count) {
        return switch (c) {
            case 'y' -> (app, y, M, d, H, m, s, S) -> {
                if (count == 2) {
                    appendInt(y % 100, 2, app);
                } else {
                    appendInt(y, count, app);
                }
            };
            case 'M' -> (app, y, M, d, H, m, s, S) -> appendInt(M, count, app);
            case 'd' -> (app, y, M, d, H, m, s, S) -> appendInt(d, count, app);
            case 'H' -> (app, y, M, d, H, m, s, S) -> appendInt(H, count, app);
            case 'm' -> (app, y, M, d, H, m, s, S) -> appendInt(m, count, app);
            case 's' -> (app, y, M, d, H, m, s, S) -> appendInt(s, count, app);
            case 'S' -> (app, y, M, d, H, m, s, S) -> appendInt(S, count, app);
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

    private static void appendInt(int val, int digits, Appendable app) throws IOException {
        switch (digits) {
            case 2 -> app
                    .append(DIGIT_TENS[val])
                    .append(DIGIT_ONES[val]);
            case 3 -> {
                int q = val / 100;
                app.append(DIGIT_ONES[q]);
                q = val % 100;
                app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
            }
            case 4 -> {
                int q = val / 100;
                app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
                q = val % 100;
                app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
            }
            default -> app.append(Integer.toString(val));
        }
    }

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
