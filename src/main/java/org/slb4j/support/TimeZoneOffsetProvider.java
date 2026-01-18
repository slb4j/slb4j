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
        OffsetInterval startup = intervals[startupIdx];
        if (timestamp >= startup.start && timestamp < startup.end) {
            return startup.offset;
        }

        // Directional search: search only the half of the array where the timestamp could be
        boolean isPast = timestamp < startup.start;
        int start = isPast ? 0 : startupIdx + 1;
        int end = isPast ? startupIdx : intervals.length;

        for (int i = start; i < end; i++) {
            if (intervals[i].contains(timestamp)) {
                return intervals[i].offset;
            }
        }

        // Fallback using temporary instant
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
