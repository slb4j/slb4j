package org.slb4j.support;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;

public class TimeZoneOffsetProvider {
    private final ZoneId zoneId;
    private final OffsetInterval[] intervals;
    private final int startupIdx;

    private record OffsetInterval(long start, long end, int offset) {
        boolean contains(long ts) { return ts >= start && ts < end; }
    }

    public TimeZoneOffsetProvider(ZoneId zoneId) {
        this.zoneId = zoneId;
        this.intervals = precomputeIntervals(zoneId);

        // Find the index valid at the moment the app starts
        int foundIdx = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i].contains(now)) {
                foundIdx = i;
                break;
            }
        }
        this.startupIdx = foundIdx;
    }

    public int getOffset(long timestamp) {
        // 1. Check the interval that was valid at startup (99% of the time)
        OffsetInterval startup = intervals[startupIdx];
        if (timestamp >= startup.start && timestamp < startup.end) {
            return startup.offset;
        }

        // 2. Fallback: Search the array.
        // No volatile, no state updates. Just a pure, thread-safe read.
        int start = timestamp < startup.start ? 0 : startupIdx + 1;
        int end = timestamp < startup.start ? startupIdx : intervals.length;
        for (int i = start; i < end; i++) {
            OffsetInterval interval = intervals[i];
            if (interval.contains(timestamp)) {
                return interval.offset;
            }
        }

        return zoneId.getRules().getOffset(Instant.ofEpochMilli(timestamp)).getTotalSeconds();
    }

    private OffsetInterval[] precomputeIntervals(ZoneId zoneId) {
        List<OffsetInterval> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        // One year look-ahead
        long endLimit = now + (366L * 24 * 60 * 60 * 1000);

        var rules = zoneId.getRules();
        Instant currentInstant = Instant.ofEpochMilli(now);
        long intervalStart = Long.MIN_VALUE;

        while (currentInstant.toEpochMilli() < endLimit) {
            ZoneOffsetTransition transition = rules.nextTransition(currentInstant);
            if (transition == null) {
                list.add(new OffsetInterval(intervalStart, Long.MAX_VALUE,
                        rules.getOffset(currentInstant).getTotalSeconds()));
                break;
            }

            long transitionMs = transition.getInstant().toEpochMilli();
            list.add(new OffsetInterval(intervalStart, transitionMs,
                    rules.getOffset(currentInstant).getTotalSeconds()));

            intervalStart = transitionMs;
            currentInstant = transition.getInstant().plusMillis(1);
        }
        return list.toArray(new OffsetInterval[0]);
    }
}
