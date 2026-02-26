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
public final class TimeOnlyTimeStampFormatter extends AbstractTimeStampFormatter {

    private final char millisSeparator;

    /**
     * Constructs an {@code ISO8601TimeStampFormatter} with custom separators for date-time and milliseconds,
     * and an optional time zone.
     *
     * @param millisSeparator the character used to separate the seconds and milliseconds components in the formatted timestamp
     * @param zoneId          the {@link ZoneId} used for formatting the timestamp, or {@code null} to default to the system time zone
     */
    public TimeOnlyTimeStampFormatter(char millisSeparator, @Nullable ZoneId zoneId) {
        super(zoneId == null ? ZoneId.systemDefault() : zoneId);
        this.millisSeparator = millisSeparator;
    }

    @Override
    protected void appendTo(Appendable app, int y, int M, int d, int H, int m, int s, int S, int offsetSeconds) throws IOException {
        int q1 = S / 100;
        int q2 = S % 100;

        app.append(DIGIT_TENS[H]).append(DIGIT_ONES[H]);
        app.append(':');
        app.append(DIGIT_TENS[m]).append(DIGIT_ONES[m]);
        app.append(':');
        app.append(DIGIT_TENS[s]).append(DIGIT_ONES[s]);
        app.append(millisSeparator);
        app.append(DIGIT_ONES[q1]);
        app.append(DIGIT_TENS[q2]).append(DIGIT_ONES[q2]);
    }
}
