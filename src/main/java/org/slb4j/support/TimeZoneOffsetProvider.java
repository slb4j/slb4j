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

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides optimized time zone offset resolution for a given {@link ZoneId}.
 * The class precomputes and caches offset intervals based on zone transitions
 * to facilitate efficient lookup of the offset for a given timestamp. The precomputation
 * spans a limited time range to minimize resource consumption while covering typical use cases.
 */
public class TimeZoneOffsetProvider {
    private static final long PRECOMPUTE_WINDOW_MS = 366L * 24 * 60 * 60 * 1000;

    private final ZoneId zoneId;
    private final OffsetInterval[] intervals;
    private final int startupIdx;

    private record OffsetInterval(long start, long end, int offset) {
        boolean contains(long ts) {return ts >= start && ts < end;}
    }

    /**
     * Constructs a TimeZoneOffsetProvider instance for the specified ZoneId.
     * This constructor precomputes time offset intervals for the given time zone
     * and determines the applicable interval at the time of application startup.
     *
     * @param zoneId the time zone for which time offset intervals will be calculated
     */
    public TimeZoneOffsetProvider(ZoneId zoneId) {
        this.zoneId = zoneId;
        this.intervals = precomputeIntervals(zoneId);

        // Find the index valid at the moment the app starts
        int foundIdx = -1;
        long now = System.currentTimeMillis();
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i].contains(now)) {
                foundIdx = i;
                break;
            }
        }
        this.startupIdx = foundIdx;
    }

    /**
     * Calculates the total time offset, in seconds, for the specified timestamp.
     * <p>
     * The method determines the appropriate offset by checking if the timestamp
     * falls within precomputed intervals. If no matching interval is found, it
     * calculates the offset using the time zone rules for the given timestamp.
     *
     * @param timestamp the timestamp, in milliseconds since the epoch, for which
     *                  the time offset is to be determined.
     * @return the total time offset, in seconds, for the given timestamp.
     */
    public int getOffset(long timestamp) {
        if (startupIdx >= 0) {
            OffsetInterval startup = intervals[startupIdx];
            if (timestamp >= startup.start && timestamp < startup.end) {
                return startup.offset;
            }
        }

        if (startupIdx >= 0) {
            // Directional search: search only the half of the array where the timestamp could be
            OffsetInterval startup = intervals[startupIdx];
            int start;
            int end;
            if (timestamp < startup.start) {
                start = 0;
                end = startupIdx;
            } else {
                start = startupIdx + 1;
                end = intervals.length;
            }

            for (int i = start; i < end; i++) {
                if (intervals[i].contains(timestamp)) {
                    return intervals[i].offset;
                }
            }
        } else {
            for (OffsetInterval interval : intervals) {
                if (interval.contains(timestamp)) {
                    return interval.offset;
                }
            }
        }

        // Fallback using temporary instant
        return zoneId.getRules().getOffset(Instant.ofEpochMilli(timestamp)).getTotalSeconds();
    }

    private static OffsetInterval[] precomputeIntervals(ZoneId zoneId) {
        List<OffsetInterval> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        long startLimit = now - PRECOMPUTE_WINDOW_MS;
        long endLimit = now + PRECOMPUTE_WINDOW_MS;

        var rules = zoneId.getRules();
        Instant currentInstant = Instant.ofEpochMilli(startLimit);
        int currentOffset = rules.getOffset(currentInstant).getTotalSeconds();
        long intervalStart = startLimit;

        while (intervalStart < endLimit) {
            ZoneOffsetTransition transition = rules.nextTransition(currentInstant);
            if (transition == null) {
                list.add(new OffsetInterval(intervalStart, endLimit, currentOffset));
                break;
            }

            long transitionMs = transition.getInstant().toEpochMilli();
            if (transitionMs >= endLimit) {
                list.add(new OffsetInterval(intervalStart, endLimit, currentOffset));
                break;
            }

            list.add(new OffsetInterval(intervalStart, transitionMs,
                    currentOffset));

            intervalStart = transitionMs;
            currentInstant = transition.getInstant().plusMillis(1);
            currentOffset = rules.getOffset(currentInstant).getTotalSeconds();
        }
        return list.toArray(OffsetInterval[]::new);
    }
}
