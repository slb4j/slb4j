package org.slb4j.support.formatter;

import org.slb4j.support.IoStringBuilder;
import org.slb4j.support.TimeStampFormatter;
import org.slb4j.support.TimeZoneOffsetProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Base class for high-performance timestamp formatters.
 */
public abstract sealed class AbstractTimeStampFormatter implements TimeStampFormatter permits ISO8601TimeStampFormatter, PatternTimeStampFormatter, TimeOnlyTimeStampFormatter {

    /**
     * Represents a part of a formatted timestamp.
     */
    @FunctionalInterface
    interface Part {
        void append(Appendable app, int y, int M, int d, int H, int m, int s, int S, int offsetSeconds) throws IOException;
    }

    /**
     * Digit tens for fast integer formatting.
     */
    static final char[] DIGIT_TENS = {
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

    /**
     * Digit ones for fast integer formatting.
     */
    static final char[] DIGIT_ONES = {
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
    /**
     * Mapping from Zeller's congruence result to {@link java.util.Calendar} day of week.
     */
    private static final int[] ZELLER_TO_CALENDAR = {7, 1, 2, 3, 4, 5, 6};

    /**
     * Provider for time zone offsets.
     */
    protected final TimeZoneOffsetProvider offsetProvider;

    /**
     * Creates a new formatter with the given zone ID.
     *
     * @param zoneId the zone ID to use
     */
    protected AbstractTimeStampFormatter(ZoneId zoneId) {this.offsetProvider = new TimeZoneOffsetProvider(zoneId);}

    /**
     * {@inheritDoc}
     */
    @Override
    public final void appendTo(long timestamp, Appendable app) throws IOException {
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

        switch (app) {
            case IoStringBuilder buf -> appendTo(buf, y, m, d, hour, minute, second, millis, offset);
            default -> appendTo(app, y, m, d, hour, minute, second, millis, offset);
        }
    }

    /**
     * Formats the timestamp components into the given {@link Appendable}.
     *
     * @param app           the appendable to write to
     * @param y             the year
     * @param m             the month (1-12)
     * @param d             the day of month (1-31)
     * @param hour          the hour (0-23)
     * @param minute        the minute (0-59)
     * @param second        the second (0-59)
     * @param millis        the milliseconds (0-999)
     * @param offsetSeconds the timezone offset in seconds
     * @throws IOException if an I/O error occurs
     */
    protected abstract void appendTo(Appendable app, int y, int m, int d, int hour, int minute, int second, int millis, int offsetSeconds) throws IOException;

    /**
     * Formats the timestamp components into the given {@link IoStringBuilder}.
     *
     * @param buf           the buffer to write to
     * @param y             the year
     * @param m             the month (1-12)
     * @param d             the day of month (1-31)
     * @param hour          the hour (0-23)
     * @param minute        the minute (0-59)
     * @param second        the second (0-59)
     * @param millis        the milliseconds (0-999)
     * @param offsetSeconds the timezone offset in seconds
     * @throws IOException if an I/O error occurs
     */
    protected void appendTo(IoStringBuilder buf, int y, int m, int d, int hour, int minute, int second, int millis, int offsetSeconds) throws IOException {
        appendTo((Appendable) buf, y, m, d, hour, minute, second, millis, offsetSeconds);
    }

    /**
     * Returns the day of week for the given date using Zeller's congruence.
     *
     * @param y the year
     * @param m the month (1-12)
     * @param d the day of month (1-31)
     * @return the day of week (1=Sunday, ..., 7=Saturday)
     */
    protected static int getDayOfWeek(int y, int m, int d) {
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

    /**
     * Returns the month names for the given locale.
     *
     * @param locale the locale
     * @param full   whether to return full names or short names
     * @return an array of 12 month names
     */
    protected static String[] getMonthNames(Locale locale, boolean full) {
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

    /**
     * Returns the day of week names for the given locale.
     *
     * @param locale the locale
     * @param full   whether to return full names or short names
     * @return an array of day of week names
     */
    protected static String[] getDayNames(Locale locale, boolean full) {
        java.text.DateFormatSymbols symbols = java.text.DateFormatSymbols.getInstance(locale);
        return full ? symbols.getWeekdays() : symbols.getShortWeekdays();
    }

    /**
     * Returns the AM/PM strings for the given locale.
     *
     * @param locale the locale
     * @return an array of 2 strings: AM and PM
     */
    protected static String[] getAmPmStrings(Locale locale) {
        java.text.DateFormatSymbols symbols = java.text.DateFormatSymbols.getInstance(locale);
        return symbols.getAmPmStrings();
    }

    /**
     * Returns the 12-hour clock hour (1-12) for the given 24-hour clock hour.
     *
     * @param H the hour (0-23)
     * @return the hour (1-12)
     */
    protected static int getHour12(int H) {
        int hour12 = H % 12;
        return hour12 == 0 ? 12 : hour12;
    }

    /**
     * Appends an integer with a fixed number of digits.
     *
     * @param app    the appendable
     * @param digits the number of digits (2, 3, or 4)
     * @param val    the value to append
     * @throws IOException if an I/O error occurs
     */
    protected static void appendInt(Appendable app, int digits, int val) throws IOException {
        switch (digits) {
            case 2 -> AbstractTimeStampFormatter.appendInt2(app, val);
            case 3 -> AbstractTimeStampFormatter.appendInt3(app, val);
            case 4 -> AbstractTimeStampFormatter.appendInt4(app, val);
            default -> app.append(Integer.toString(val));
        }
    }

    /**
     * Appends a 4-digit integer.
     *
     * @param app the appendable
     * @param val the value to append
     * @throws IOException if an I/O error occurs
     */
    protected static void appendInt4(Appendable app, int val) throws IOException {
        int q = val / 100;
        app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
        q = val % 100;
        app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
    }

    /**
     * Appends a 3-digit integer.
     *
     * @param app the appendable
     * @param val the value to append
     * @throws IOException if an I/O error occurs
     */
    protected static void appendInt3(Appendable app, int val) throws IOException {
        int q = val / 100;
        app.append(DIGIT_ONES[q]);
        q = val % 100;
        app.append(DIGIT_TENS[q]).append(DIGIT_ONES[q]);
    }

    /**
     * Appends a 2-digit integer.
     *
     * @param app the appendable
     * @param val the value to append
     * @return the appendable
     * @throws IOException if an I/O error occurs
     */
    protected static Appendable appendInt2(Appendable app, int val) throws IOException {
        return app.append(DIGIT_TENS[val]).append(DIGIT_ONES[val]);
    }

    /**
     * Appends the timezone offset in ISO 8601 format (e.g., "Z" or "+HH:mm").
     *
     * @param app           the appendable
     * @param offsetSeconds the offset in seconds
     * @throws IOException if an I/O error occurs
     */
    protected static void appendOffset(Appendable app, int offsetSeconds) throws IOException {
        if (offsetSeconds == 0) {
            app.append('Z');
            return;
        }

        if (offsetSeconds < 0) {
            app.append('-');
            offsetSeconds = -offsetSeconds;
        } else {
            app.append('+');
        }

        int totalMinutes = offsetSeconds / 60;
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;

        appendInt2(app, hours);
        app.append(':');
        appendInt2(app, minutes);
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
     * according to the internal configuration of this formatter.
     * @throws UncheckedIOException if an I/O error occurs while constructing
     *                              the formatted string.
     */
    @Override
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
