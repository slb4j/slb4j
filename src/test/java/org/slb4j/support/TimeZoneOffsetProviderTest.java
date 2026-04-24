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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRules;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TimeZoneOffsetProviderTest {

    @Test
    void testOffsetForZoneWithDstUsesCorrectPastOffset() {
        // DST-enabled zone where winter/summer offsets differ.
        ZoneId zoneId = ZoneId.of("Europe/Berlin");
        ZoneRules rules = zoneId.getRules();
        TimeZoneOffsetProvider provider = new TimeZoneOffsetProvider(zoneId);

        Instant now = Instant.now();
        int currentOffsetSeconds = rules.getOffset(now).getTotalSeconds();

        Instant probeWithDifferentOffset = null;
        // Find a recent timestamp that is in the opposite DST state from "now".
        for (int month = 1; month <= 24; month++) {
            Instant probe = now.minus(month * 30L, ChronoUnit.DAYS);
            if (rules.getOffset(probe).getTotalSeconds() != currentOffsetSeconds) {
                probeWithDifferentOffset = probe;
                break;
            }
        }

        assertNotNull(probeWithDifferentOffset, "No past timestamp with different DST offset found");

        int expected = rules.getOffset(probeWithDifferentOffset).getTotalSeconds();
        int actual = provider.getOffset(probeWithDifferentOffset.toEpochMilli());

        // Provider must match zone rules for the probed timestamp, not for "now".
        assertEquals(expected, actual,
                "Provider must use the timestamp-specific offset in DST zones");
    }

    @Test
    void testOffsetForZoneWithoutDstMatchesRules() {
        // Control case: constant-offset zone must always match.
        ZoneId zoneId = ZoneId.of("UTC");
        ZoneRules rules = zoneId.getRules();
        TimeZoneOffsetProvider provider = new TimeZoneOffsetProvider(zoneId);

        long[] timestamps = {
                0L,
                1705574640000L,
                1737194640000L,
                1609459200000L,
                -31536000000L
        };

        for (long timestamp : timestamps) {
            int expected = rules.getOffset(Instant.ofEpochMilli(timestamp)).getTotalSeconds();
            int actual = provider.getOffset(timestamp);
            assertEquals(expected, actual,
                    "Offset mismatch for timestamp " + timestamp + " in zone without DST");
        }
    }
}
