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

import org.jspecify.annotations.Nullable;
import org.slb4j.support.IoStringBuilder;

import java.io.IOException;
import java.time.ZoneId;

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
public final class ISO8601TimeStampFormatter extends AbstractTimeStampFormatter {

    private final char dateTimeSeparator;
    private final char millisSeparator;
    private final boolean includeOffset;

    /**
     * Constructs an {@code ISO8601TimeStampFormatter} with custom separators for date-time and milliseconds,
     * and an optional time zone.
     *
     * @param dateTimeSeparator the character used to separate the date and time components in the formatted timestamp
     * @param millisSeparator   the character used to separate the seconds and milliseconds components in the formatted timestamp
     * @param inlcudeOffset     whether to include the time zone offset in the formatted timestamp
     * @param zoneId            the {@link ZoneId} used for formatting the timestamp, or {@code null} to default to the system time zone
     */
    public ISO8601TimeStampFormatter(char dateTimeSeparator, char millisSeparator, boolean inlcudeOffset, @Nullable ZoneId zoneId) {
        super(zoneId == null ? ZoneId.systemDefault() : zoneId);
        this.dateTimeSeparator = dateTimeSeparator;
        this.millisSeparator = millisSeparator;
        this.includeOffset = inlcudeOffset;
    }

    @Override
    protected void appendTo(Appendable app, int y, int M, int d, int H, int m, int s, int S, int offsetSeconds) throws IOException {
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
        if (includeOffset) {
            appendOffset(app, offsetSeconds);
        }
    }

    @Override
    protected void appendTo(IoStringBuilder buf, int y, int M, int d, int H, int m, int s, int S, int offsetSeconds) throws IOException {
        int q = y / 100;
        buf.put(DIGIT_TENS[q]).put(DIGIT_ONES[q]);
        q = y % 100;
        buf.put(DIGIT_TENS[q]).put(DIGIT_ONES[q]);
        buf.put('-');
        buf.put(DIGIT_TENS[M]).put(DIGIT_ONES[M]);
        buf.put('-');
        buf.put(DIGIT_TENS[d]).put(DIGIT_ONES[d]);
        buf.put(dateTimeSeparator);
        buf.put(DIGIT_TENS[H]).put(DIGIT_ONES[H]);
        buf.put(':');
        buf.put(DIGIT_TENS[m]).put(DIGIT_ONES[m]);
        buf.put(':');
        buf.put(DIGIT_TENS[s]).put(DIGIT_ONES[s]);
        buf.put(millisSeparator);
        int q1 = S / 100;
        buf.put(DIGIT_ONES[q1]);
        q1 = S % 100;
        buf.put(DIGIT_TENS[q1]).put(DIGIT_ONES[q1]);

        if (includeOffset) {
            appendOffset(buf, offsetSeconds);
        }
    }
}
