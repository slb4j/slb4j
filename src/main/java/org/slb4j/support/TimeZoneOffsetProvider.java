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

    /**
     * Calculates the total time offset, in seconds, for the specified timestamp.
     *
     * The method determines the appropriate offset by checking if the timestamp
     * falls within precomputed intervals. If no matching interval is found, it
     * calculates the offset using the time zone rules for the given timestamp.
     *
     * @param timestamp the timestamp, in milliseconds since the epoch, for which
     *                  the time offset is to be determined.
     * @return the total time offset, in seconds, for the given timestamp.
     */
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

    private static OffsetInterval[] precomputeIntervals(ZoneId zoneId) {
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
        return list.toArray(OffsetInterval[]::new);
    }
}
