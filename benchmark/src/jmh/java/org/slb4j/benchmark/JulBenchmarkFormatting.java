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
package org.slb4j.benchmark;

import java.util.logging.SimpleFormatter;

final class JulBenchmarkFormatting {

    private static final String SIMPLE_FORMATTER_PROPERTY = "java.util.logging.SimpleFormatter.format";
    private static final String COMPACT_FORMAT = "%1$tH:%1$tM:%1$tS.%1$tL %2$s %4$s: %5$s%6$s%n";
    private static final String DEFAULT_FORMAT = "%1$tb %1$te, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s %4$s: %5$s%6$s%n";
    private static final String DETAILED_FORMAT = "%1$tb %1$te, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %3$s %2$s %4$s: %5$s%6$s%n";

    private JulBenchmarkFormatting() {
    }

    static synchronized SimpleFormatter newFormatter(String format) {
        String previousFormat = System.getProperty(SIMPLE_FORMATTER_PROPERTY);
        System.setProperty(SIMPLE_FORMATTER_PROPERTY, patternFor(format));
        try {
            return new SimpleFormatter();
        } finally {
            if (previousFormat == null) {
                System.clearProperty(SIMPLE_FORMATTER_PROPERTY);
            } else {
                System.setProperty(SIMPLE_FORMATTER_PROPERTY, previousFormat);
            }
        }
    }

    private static String patternFor(String format) {
        return switch (format) {
            case "COMPACT" -> COMPACT_FORMAT;
            case "DEFAULT" -> DEFAULT_FORMAT;
            case "DETAILED" -> DETAILED_FORMAT;
            default -> throw new IllegalArgumentException("Unknown JUL benchmark format: " + format);
        };
    }
}
